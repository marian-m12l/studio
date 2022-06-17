/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.mutiny.ext.web.Router;
import studio.config.StudioConfig;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.utils.PackAssetsCompression;
import studio.core.v1.utils.PackFormat;
import studio.core.v1.utils.exception.StoryTellerException;
import studio.core.v1.writer.fs.FsStoryPackWriter;
import studio.driver.fs.FileUtils;
import studio.metadata.DatabaseMetadataService;
import studio.metadata.DatabasePackMetadata;
import studio.webui.model.LibraryDTOs.LibraryPackDTO;
import studio.webui.model.LibraryDTOs.MetaPackDTO;
import studio.webui.model.LibraryDTOs.PathDTO;
import studio.webui.model.LibraryDTOs.UuidPacksDTO;

@ApplicationScoped
public class LibraryService {

    private static final Logger LOGGER = LogManager.getLogger(LibraryService.class);

    private static final Path libraryPath = libraryPath();
    private static final Path tmpDirPath = tmpDirPath();

    @Inject
    DatabaseMetadataService databaseMetadataService;

    public void init(@Observes Router router) {
        // Create the local library folder if needed
        if (!Files.isDirectory(libraryPath)) {
            try {
                Files.createDirectories(libraryPath);
            } catch (IOException e) {
                throw new StoryTellerException("Failed to initialize local library", e);
            }
        }

        // Create the temp folder if needed
        if (!Files.isDirectory(tmpDirPath)) {
            try {
                Files.createDirectories(tmpDirPath);
            } catch (IOException e) {
                throw new StoryTellerException("Failed to initialize temp folder", e);
            }
        }
    }

    public PathDTO infos() {
        PathDTO p = new PathDTO();
        p.setPath(libraryPath.toString());
        return p;
    }

    public List<UuidPacksDTO> packs() {
        // Check that local library folder exists
        if (!Files.isDirectory(libraryPath)) {
            return Collections.emptyList();
        }
        // List pack files in library folder
        try (Stream<Path> paths = Files.walk(libraryPath, 1).filter(p -> p != libraryPath)) {
            // sort by timestamp DESC (=newest first)
            Comparator<LibraryPackDTO> newestComparator = Comparator.comparingLong(LibraryPackDTO::getTimestamp)
                    .reversed();

            // Group pack by uuid
            Map<String, List<LibraryPackDTO>> metadataByUuid = paths
                    // debuging
                    .filter(p -> {
                        LOGGER.info("Read metadata from `{}`", p.getFileName());
                        return true;
                    })
                    // actual read
                    .map(this::readMetadata)
                    // filter empty
                    .filter(Optional::isPresent).map(Optional::get)
                    // sort by timestamp DESC (=newer first)
                    .sorted(newestComparator)
                    // Group packs by UUID
                    .collect(Collectors.groupingBy(p -> p.getMetadata().getUuid()));

            // Converts metadata to Json
            List<UuidPacksDTO> jsonMetasByUuid = metadataByUuid.entrySet().stream()
                    // convert
                    .map(e -> {
                        // find first zip pack
                        e.getValue().stream()
                                // get Metadata
                                .map(LibraryPackDTO::getMetadata)
                                // only zip
                                .filter(meta -> meta.getFormat() == PackFormat.ARCHIVE) //
                                // update database with newest zip
                                .findFirst().ifPresent(meta -> {
                                    LOGGER.debug("Refresh metadata from zip for {} ({})", meta.getUuid(),
                                            meta.getTitle());
                                    String thumbBase64 = Optional.ofNullable(meta.getThumbnail()).map(this::base64)
                                            .orElse(null);
                                    databaseMetadataService.refreshUnofficialCache(new DatabasePackMetadata( //
                                            meta.getUuid(), meta.getTitle(), meta.getDescription(), thumbBase64,
                                            false));
                                });
                        // Convert to MetaPackDTO
                        List<MetaPackDTO> jsonMetaList = e.getValue().stream() //
                                .map(this::toDto) //
                                .collect(Collectors.toList());

                        return new UuidPacksDTO(e.getKey(), jsonMetaList);
                    }) //
                    .collect(Collectors.toList());
            // persist unofficial database cache (if needed)
            databaseMetadataService.persistUnofficialDatabase();
            return jsonMetasByUuid;
        } catch (IOException e) {
            LOGGER.error("Failed to read packs from local library", e);
            throw new StoryTellerException(e);
        }
    }

    public Path getPackFile(String packPath) {
        return libraryPath.resolve(packPath);
    }

    public Path addConvertedPack(String packPath, PackFormat packFormat, boolean allowEnriched) {
        if (PackFormat.RAW == packFormat) {
            return addConvertedRawPackFile(packPath, allowEnriched);
        }
        if (PackFormat.FS == packFormat) {
            return addConvertedFsPackFile(packPath);
        }
        if (PackFormat.ARCHIVE == packFormat) {
            return addConvertedArchivePackFile(packPath);
        }
        throw new StoryTellerException("Unknown pack format " + packFormat);
    }

    public Path addConvertedRawPackFile(String packFile, boolean allowEnriched) {
        Path packPath = libraryPath.resolve(packFile);
        PackFormat inputFormat = PackFormat.fromPath(packPath);
        PackFormat outputFormat = PackFormat.RAW;
        assertFormat(inputFormat, outputFormat);
        LOGGER.info("Pack is in {} format. Converting to {} format", inputFormat, outputFormat);
        try {
            // Packs must first be converted to raw format
            LOGGER.info("Reading {} format pack", inputFormat);
            StoryPack storyPack = inputFormat.getReader().read(packPath);

            // Uncompress pack assets
            if (PackAssetsCompression.hasCompressedAssets(storyPack)) {
                LOGGER.info("Uncompressing pack assets");
                PackAssetsCompression.processUncompressed(storyPack);
            }

            Path tmp = createTempFile(packFile, outputFormat.getExtension());
            LOGGER.info("Writing {} format pack, using temporary file: {}", outputFormat, tmp);
            outputFormat.getWriter().write(storyPack, tmp, allowEnriched);

            String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis()
                    + outputFormat.getExtension();
            Path destinationPath = libraryPath.resolve(destinationFileName);
            LOGGER.info("Moving {} format pack into local library: {}", outputFormat, destinationPath);
            Files.move(tmp, destinationPath);

            return destinationPath;
        } catch (Exception e) {
            String msg = "Failed to convert " + inputFormat + " format pack to " + outputFormat + " format";
            LOGGER.error(msg, e);
            throw new StoryTellerException(msg, e);
        }
    }

    public Path addConvertedArchivePackFile(String packFile) {
        Path packPath = libraryPath.resolve(packFile);
        PackFormat inputFormat = PackFormat.fromPath(packPath);
        PackFormat outputFormat = PackFormat.ARCHIVE;
        assertFormat(inputFormat, outputFormat);
        LOGGER.info("Pack is in {} format. Converting to {} format", inputFormat, outputFormat);
        try {
            // Packs must first be converted to raw format
            LOGGER.info("Reading {} format pack", inputFormat);
            StoryPack storyPack = inputFormat.getReader().read(packPath);
            // Compress pack assets
            if (inputFormat == PackFormat.RAW) {
                LOGGER.info("Compressing pack assets");
                PackAssetsCompression.processCompressed(storyPack);
            }

            String zipName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".zip";
            Path tmp = tmpDirPath.resolve(zipName);

            LOGGER.info("Writing {} format pack, using temporary file: {}", outputFormat, tmp);
            outputFormat.getWriter().write(storyPack, tmp, true);

            Path destinationPath = libraryPath.resolve(zipName);
            LOGGER.info("Moving {} format pack into local library: {}", outputFormat, destinationPath);
            Files.move(tmp, destinationPath);

            return destinationPath;
        } catch (Exception e) {
            String msg = "Failed to convert " + inputFormat + " format pack to " + outputFormat + " format";
            LOGGER.error(msg, e);
            throw new StoryTellerException(msg, e);
        }
    }

    public Path addConvertedFsPackFile(String packFile) {
        Path packPath = libraryPath.resolve(packFile);
        PackFormat inputFormat = PackFormat.fromPath(packPath);
        PackFormat outputFormat = PackFormat.FS;
        assertFormat(inputFormat, outputFormat);
        LOGGER.info("Pack is in {} format. Converting to {} format", inputFormat, outputFormat);
        try {
            // Packs must first be converted to raw format
            LOGGER.info("Reading {} format pack", inputFormat);
            StoryPack storyPack = inputFormat.getReader().read(packPath);

            // Prepare assets (RLE-encoded BMP, audio must already be MP3)
            LOGGER.info("Converting assets if necessary");
            PackAssetsCompression.processFirmware2dot4(storyPack);

            Path tmp = createTempDirectory(packFile);
            LOGGER.info("Writing {} format pack, using temporary folder: {}", outputFormat, tmp);
            // should we not keep uuid instead ?
            Path tmpPath = FsStoryPackWriter.createPackFolder(storyPack, tmp);
            outputFormat.getWriter().write(storyPack, tmpPath, true);

            String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis();
            Path destinationPath = libraryPath.resolve(destinationFileName);
            LOGGER.info("Moving {} format pack into local library: {}", outputFormat, destinationPath);
            Files.move(tmpPath, destinationPath);

            return destinationPath;
        } catch (Exception e) {
            String msg = "Failed to convert " + inputFormat + " format pack to " + outputFormat + " format";
            LOGGER.error(msg, e);
            throw new StoryTellerException(msg, e);
        }
    }

    public boolean addPackFile(String destPath, String uploadedFilePath) {
        try {
            LOGGER.info("Add pack {} from {}", destPath, uploadedFilePath);
            // Copy temporary file to local library
            Path src = Path.of(uploadedFilePath);
            Path dest = libraryPath.resolve(destPath);
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to add pack to local library", e);
            throw new StoryTellerException(e);
        }
    }

    public boolean deletePack(String packPath) {
        LOGGER.info("Delete pack '{}'", packPath);
        if (!Files.isDirectory(libraryPath)) {
            return false;
        }
        Path packFile = libraryPath.resolve(packPath);
        try {
            if (Files.isDirectory(packFile)) {
                FileUtils.deleteDirectory(packFile);
            } else {
                Files.deleteIfExists(packFile);
            }
            return true;
        } catch (IOException e) {
            throw new StoryTellerException("Failed to remove pack from library", e);
        }
    }

    public static Path libraryPath() {
        return Path.of(StudioConfig.STUDIO_LIBRARY.getValue());
    }

    public static Path tmpDirPath() {
        return Path.of(StudioConfig.STUDIO_TMPDIR.getValue());
    }

    private Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(tmpDirPath, prefix, suffix);
    }

    private Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(tmpDirPath, prefix);
    }

    private void assertFormat(PackFormat inputFormat, PackFormat outputFormat) {
        if (inputFormat == outputFormat) {
            String msg = "Pack is already in " + outputFormat + " format";
            LOGGER.error(msg);
            throw new StoryTellerException(msg);
        }
    }

    private String base64(byte[] thumbnail) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(thumbnail);
    }

    private Optional<LibraryPackDTO> readMetadata(Path path) {
        // Select reader
        PackFormat inputFormat = PackFormat.fromPath(path);
        // Ignore other files
        if (inputFormat == null) {
            return Optional.empty();
        }
        // read Metadata
        try {
            LOGGER.debug("Reading metadata {} from pack: {}", inputFormat, path);
            StoryPackMetadata meta = inputFormat.getReader().readMetadata(path);
            if (meta != null) {
                meta.setSectorSize((int) Math.ceil(Files.size(path) / 512d));
                return Optional.of(new LibraryPackDTO(path, Files.getLastModifiedTime(path).toMillis(), meta));
            }
        } catch (IOException e) {
            LOGGER.atError().withThrowable(e).log("Failed to read metadata {} from pack: {}", inputFormat, path);
        }
        // Ignore other files OR read error
        return Optional.empty();
    }

    private MetaPackDTO toDto(LibraryPackDTO pack) {
        StoryPackMetadata spMeta = pack.getMetadata();
        MetaPackDTO mp = new MetaPackDTO();
        mp.setFormat(spMeta.getFormat().getLabel());
        mp.setUuid(spMeta.getUuid());
        mp.setVersion(spMeta.getVersion());
        mp.setPath(pack.getPath().getFileName().toString());
        mp.setTimestamp(pack.getTimestamp());
        mp.setNightModeAvailable(spMeta.isNightModeAvailable());
        mp.setSectorSize(spMeta.getSectorSize());
        mp.setTitle(spMeta.getTitle());
        mp.setDescription(spMeta.getDescription());
        Optional.ofNullable(spMeta.getThumbnail()).ifPresent(this::base64);

        return databaseMetadataService.getPackMetadata(spMeta.getUuid()).map(metadata -> {
            mp.setTitle(metadata.getTitle());
            mp.setDescription(metadata.getDescription());
            mp.setImage(metadata.getThumbnail());
            mp.setOfficial(metadata.isOfficial());
            return mp;
        }).orElse(mp);
    }

}
