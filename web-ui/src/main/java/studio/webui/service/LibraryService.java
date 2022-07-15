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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.service.PackFormat;
import studio.core.v1.utils.PackAssetsCompression;
import studio.core.v1.utils.io.FileUtils;
import studio.driver.model.MetaPackDTO;
import studio.metadata.DatabaseMetadataDTOs.DatabasePackMetadata;
import studio.metadata.DatabaseMetadataService;
import studio.webui.model.LibraryDTOs.LibraryPackDTO;
import studio.webui.model.LibraryDTOs.PathDTO;
import studio.webui.model.LibraryDTOs.UuidPacksDTO;

@ApplicationScoped
public class LibraryService {

    private static final Logger LOGGER = LogManager.getLogger(LibraryService.class);

    @Inject
    DatabaseMetadataService databaseMetadataService;

    @ConfigProperty(name = "studio.library")
    Path libraryPath;

    @ConfigProperty(name = "studio.tmpdir")
    Path tmpDirPath;

    public void init(@Observes StartupEvent ev) {
        LOGGER.info("library path : {} (tmpdir path : {})", libraryPath, tmpDirPath);
        // Create the local library folder if needed
        FileUtils.createDirectories("Failed to initialize local library", libraryPath);
        // Create the temp folder if needed
        FileUtils.createDirectories("Failed to initialize temp folder", tmpDirPath);
    }

    public PathDTO infos() {
        return new PathDTO(libraryPath.toString());
    }

    public List<UuidPacksDTO> packs() {
        // List pack files in library folder
        try (Stream<Path> paths = Files.walk(libraryPath, 1).filter(p -> p != libraryPath)) {

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
                    .sorted(Comparator.comparingLong(LibraryPackDTO::getTimestamp).reversed())
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
                                    databaseMetadataService.updateLibrary(new DatabasePackMetadata( //
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
            databaseMetadataService.persistLibrary();
            return jsonMetasByUuid;
        } catch (IOException e) {
            LOGGER.error("Failed to read packs from local library", e);
            throw new StoryTellerException(e);
        }
    }

    public Path getPackFile(String packPath) {
        return libraryPath.resolve(packPath);
    }

    public Path convertPack(String packName, PackFormat outFormat, boolean allowEnriched) {
        Path packPath = libraryPath.resolve(packName);
        PackFormat inFormat = PackFormat.fromPath(packPath);
        LOGGER.info("Pack is in {} format. Converting to {} format", inFormat, outFormat);
        // check formats
        if (inFormat == outFormat) {
            throw new StoryTellerException("Pack is already in " + outFormat + " format : " + packName);
        }
        try {
            // Read pack
            LOGGER.info("Reading {} format pack", inFormat);
            StoryPack storyPack = inFormat.getReader().read(packPath);

            // Convert
            switch (outFormat) {
            case ARCHIVE:
                // Compress pack assets
                if (inFormat == PackFormat.RAW) {
                    LOGGER.info("Compressing pack assets");
                    PackAssetsCompression.processCompressed(storyPack);
                }
                // force enriched pack
                allowEnriched = true;
                break;
            case FS:
                // Prepare assets (RLE-encoded BMP, audio must already be MP3)
                LOGGER.info("Converting assets if necessary");
                PackAssetsCompression.processFirmware2dot4(storyPack);
                // force enriched pack
                allowEnriched = true;
                break;
            case RAW:
                // Uncompress pack assets
                if (PackAssetsCompression.hasCompressedAssets(storyPack)) {
                    LOGGER.info("Uncompressing pack assets");
                    PackAssetsCompression.processUncompressed(storyPack);
                }
                break;
            }

            // Write to temporary dir
            String destName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis()
                    + outFormat.getExtension();
            Path tmpPath = tmpDirPath.resolve(destName);
            LOGGER.info("Writing {} format pack, using temporary : {}", outFormat, tmpPath);
            outFormat.getWriter().write(storyPack, tmpPath, allowEnriched);

            // Move to library
            Path destPath = libraryPath.resolve(destName);
            LOGGER.info("Moving {} format pack into local library: {}", outFormat, destPath);
            return Files.move(tmpPath, destPath);
        } catch (IOException e) {
            throw new StoryTellerException("Failed to convert " + inFormat + " pack to " + outFormat, e);
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
            throw new StoryTellerException("Failed to add pack to local library", e);
        }
    }

    public boolean deletePack(String packPath) {
        LOGGER.info("Delete pack '{}'", packPath);
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

        return databaseMetadataService.getMetadata(spMeta.getUuid()).map(metadata -> {
            mp.setTitle(metadata.getTitle());
            mp.setDescription(metadata.getDescription());
            mp.setImage(metadata.getThumbnail());
            mp.setOfficial(metadata.isOfficial());
            return mp;
        }).orElse(mp);
    }
}
