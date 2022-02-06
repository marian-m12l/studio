/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.utils.PackAssetsCompression;
import studio.core.v1.utils.PackFormat;
import studio.core.v1.utils.exception.StoryTellerException;
import studio.core.v1.writer.fs.FsStoryPackWriter;
import studio.driver.fs.FileUtils;
import studio.metadata.DatabaseMetadataService;
import studio.metadata.DatabasePackMetadata;
import studio.webui.model.LibraryPack;

public class LibraryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryService.class);

    public static final String LOCAL_LIBRARY_PROP = "studio.library";
    public static final String TMP_DIR_PROP = "studio.tmpdir";

    private static final Path libraryPath = libraryPath();
    private static final Path tmpDirPath = tmpDirPath();

    private final DatabaseMetadataService databaseMetadataService;

    public LibraryService(DatabaseMetadataService databaseMetadataService) {
        this.databaseMetadataService = databaseMetadataService;

        // Create the local library folder if needed
        if (!Files.isDirectory(libraryPath)) {
            try {
                Files.createDirectories(libraryPath);
            } catch (IOException e) {
                LOGGER.error("Failed to initialize local library", e);
                throw new IllegalStateException("Failed to initialize local library");
            }
        }

        // Create the temp folder if needed
        if (!Files.isDirectory(tmpDirPath)) {
            try {
                Files.createDirectories(tmpDirPath);
            } catch (IOException e) {
                LOGGER.error("Failed to initialize temp folder", e);
                throw new IllegalStateException("Failed to initialize temp folder");
            }
        }
    }

    public JsonObject libraryInfos() {
        return new JsonObject().put("path", libraryPath.toString());
    }

    public JsonArray packs() {
        // Check that local library folder exists
        if (!Files.isDirectory(libraryPath)) {
            return new JsonArray();
        }
        // List pack files in library folder
        try (Stream<Path> paths = Files.walk(libraryPath, 1).filter(p -> p != libraryPath)) {
            // sort by timestamp DESC (=newest first)
            Comparator<LibraryPack> newestComparator = Comparator.comparingLong(LibraryPack::getTimestamp).reversed();

            // Group pack by uuid
            Map<String, List<LibraryPack>> metadataByUuid = paths
                    // debuging
                    .filter(p -> {
                        LOGGER.info("Read metadata from `" + p.getFileName() + "`");
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
            List<JsonObject> jsonMetasByUuid = metadataByUuid.entrySet().stream()
                    // convert
                    .map(e -> {
                        // find first zip pack
                        e.getValue().stream()
                                // get Metadata
                                .map(LibraryPack::getMetadata)
                                // only zip
                                .filter(meta -> meta.getFormat() == PackFormat.ARCHIVE) //
                                // update database with newest zip
                                .findFirst().ifPresent(meta -> {
                                    LOGGER.info("Refresh metadata from zip for " + meta.getUuid() + " ("
                                            + meta.getTitle() + ")");
                                    String thumbBase64 = Optional.ofNullable(meta.getThumbnail())
                                            .map(t -> "data:image/png;base64," + Base64.getEncoder().encodeToString(t))
                                            .orElse(null);
                                    databaseMetadataService.refreshUnofficialCache(new DatabasePackMetadata( //
                                            meta.getUuid(), meta.getTitle(), meta.getDescription(), thumbBase64,
                                            false));
                                });
                        // Convert to JsonObject
                        List<JsonObject> jsonMetaList = e.getValue().stream()//
                                .map(this::libraryPackToJson)//
                                .collect(Collectors.toList());

                        return new JsonObject().put("uuid", e.getKey()).put("packs", new JsonArray(jsonMetaList));
                    }) //
                    .collect(Collectors.toList());
            // persist unofficial database cache (if needed)
            databaseMetadataService.persistUnofficialDatabase();
            // return
            return new JsonArray(jsonMetasByUuid);
        } catch (IOException e) {
            LOGGER.error("Failed to read packs from local library", e);
            throw new RuntimeException(e);
        }
    }

    public Optional<Path> getRawPackFile(String packPath) {
        return Optional.of(libraryPath.resolve(packPath));
    }

    private void assertFormat(PackFormat outputFormat) {
        String msg = "Pack is already in " + outputFormat + " format";
        LOGGER.error(msg);
        throw new StoryTellerException(msg);
    }

    public Optional<Path> addConvertedRawPackFile(String packFile, boolean allowEnriched) {
        PackFormat outputFormat = PackFormat.RAW;
        if (packFile.endsWith(".pack")) {
            assertFormat(outputFormat);
        }
        // expected input format type
        PackFormat inputFormat = packFile.endsWith(".zip") ? PackFormat.ARCHIVE : PackFormat.FS;
        LOGGER.info("Pack is in " + inputFormat + " format. Converting to " + outputFormat + " format");
        try {
            // Packs must first be converted to raw format
            Path packPath = libraryPath.resolve(packFile);
            LOGGER.info("Reading " + inputFormat + " format pack");
            StoryPack storyPack = inputFormat.getReader().read(packPath);

            // Uncompress pack assets
            if (PackAssetsCompression.hasCompressedAssets(storyPack)) {
                LOGGER.info("Uncompressing pack assets");
                PackAssetsCompression.processUncompressed(storyPack);
            }

            Path tmp = createTempFile(packFile, ".pack");
            LOGGER.info("Writing " + outputFormat + " format pack, using temporary file: " + tmp);
            outputFormat.getWriter().write(storyPack, tmp, allowEnriched);

            String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".pack";
            Path destinationPath = libraryPath.resolve(destinationFileName);
            LOGGER.info("Moving " + outputFormat + " format pack into local library: " + destinationPath);
            Files.move(tmp, destinationPath);

            return Optional.of(Paths.get(destinationFileName));
        } catch (Exception e) {
            String msg = "Failed to convert " + inputFormat + " format pack to " + outputFormat + " format";
            LOGGER.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public Optional<Path> addConvertedArchivePackFile(String packFile) {
        PackFormat outputFormat = PackFormat.ARCHIVE;
        if (packFile.endsWith(".zip")) {
            assertFormat(outputFormat);
        } 
        // expected input format type
        PackFormat inputFormat = packFile.endsWith(".pack") ? PackFormat.RAW : PackFormat.FS;
        LOGGER.info("Pack is in " + inputFormat + " format. Converting to " + outputFormat + " format");
        try {
            // Packs must first be converted to raw format
            Path packPath = libraryPath.resolve(packFile);
            LOGGER.info("Reading " + inputFormat + " format pack");
            StoryPack storyPack = inputFormat.getReader().read(packPath);
            // Compress pack assets
            if(inputFormat == PackFormat.RAW) {
                LOGGER.info("Compressing pack assets");
                PackAssetsCompression.processCompressed(storyPack);
            }

            //Path tmp = createTempFile(packFile, ".zip");
            String zipName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".zip";
            Path tmp = tmpDirPath.resolve(zipName);

            LOGGER.info("Writing " + outputFormat + " format pack, using temporary file: " + tmp);
            outputFormat.getWriter().write(storyPack, tmp, true);

            //String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".zip";
            Path destinationPath = libraryPath.resolve(zipName);
            LOGGER.info("Moving " + outputFormat + " format pack into local library: " + destinationPath);
            Files.move(tmp, destinationPath);

            return Optional.of(destinationPath);
        } catch (Exception e) {
            String msg = "Failed to convert " + inputFormat + " format pack to " + outputFormat + " format";
            LOGGER.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public Optional<Path> addConvertedFsPackFile(String packFile) {
        PackFormat outputFormat = PackFormat.FS;
        if (!packFile.endsWith(".zip") && !packFile.endsWith(".pack")) {
            assertFormat(outputFormat);
        } 
        // expected input format type
        PackFormat inputFormat = packFile.endsWith(".zip") ? PackFormat.ARCHIVE : PackFormat.RAW;
        LOGGER.info("Pack is in " + inputFormat + " format. Converting to " + outputFormat + " format");
        try {
            // Packs must first be converted to raw format
            Path packPath = libraryPath.resolve(packFile);
            LOGGER.info("Reading " + inputFormat + " format pack");
            StoryPack storyPack = inputFormat.getReader().read(packPath);

            // Prepare assets (RLE-encoded BMP, audio must already be MP3)
            LOGGER.info("Converting assets if necessary");
            PackAssetsCompression.processFirmware2dot4(storyPack);

            Path tmp = createTempDirectory(packFile);
            LOGGER.info("Writing " + outputFormat + " format pack, using temporary folder: " + tmp);
            // should we not keep uuid instead ?
            Path tmpPath = FsStoryPackWriter.createPackFolder(storyPack, tmp);
            outputFormat.getWriter().write(storyPack, tmpPath, true);

            String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis();
            Path destinationPath = libraryPath.resolve(destinationFileName);
            LOGGER.info("Moving " + outputFormat + " format pack into local library: " + destinationPath);
            Files.move(tmpPath, destinationPath);

            return Optional.of(Paths.get(destinationFileName));
        } catch (Exception e) {
            String msg = "Failed to convert " + inputFormat + " format pack to " + outputFormat + " format";
            LOGGER.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public boolean addPackFile(String destPath, String uploadedFilePath) {
        try {
            // Copy temporary file to local library
            Path src = Path.of(uploadedFilePath);
            Path dest = libraryPath.resolve(destPath);
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to add pack to local library", e);
            throw new RuntimeException(e);
        }
    }

    public boolean deletePack(String packPath) {
        if (!Files.isDirectory(libraryPath)) {
            return false;
        }
        Path packFile = libraryPath.resolve(packPath);
        if(Files.notExists(packFile)) {
            LOGGER.error("Cannot remove pack from library because it is not in the folder");
            return false;
        }
        try {
            if(Files.isDirectory(packFile)) {
                FileUtils.deleteDirectory(packFile);
            } else {
                Files.delete(packFile);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to remove pack from library", e);
            return false;
        }
    }

    public static Path libraryPath() {
        String defaultDir = System.getProperty("user.home") + "/.studio/library/";
        return Path.of(System.getProperty(LOCAL_LIBRARY_PROP, defaultDir));
    }

    private static Path tmpDirPath() {
        String defaultDir = System.getProperty("user.home") + "/.studio/tmp/";
        return Path.of(System.getProperty(TMP_DIR_PROP, defaultDir) );
    }

    private Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(tmpDirPath, prefix, suffix);
    }

    private Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(tmpDirPath, prefix);
    }

    private Optional<LibraryPack> readMetadata(Path path) {
        // Select reader
        PackFormat inputFormat = PackFormat.fromPath(path);
        // Ignore other files
        if(inputFormat == null) {
            return Optional.empty();
        }
        // read Metadata
        try {
            LOGGER.debug("Reading metadata " + inputFormat + " from pack : " + path);
            StoryPackMetadata meta = inputFormat.getReader().readMetadata(path);
            if (meta != null) {
                meta.setSectorSize((int) Math.ceil(Files.size(path) / 512d));
                return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis(), meta));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read metadata " + inputFormat + " from pack : " + path, e);
        }
        // Ignore other files OR read error
        return Optional.empty();
    }

    private JsonObject libraryPackToJson(LibraryPack pack) {
        JsonObject json = new JsonObject()
                .put("format", pack.getMetadata().getFormat().getLabel())
                .put("uuid", pack.getMetadata().getUuid())
                .put("version", pack.getMetadata().getVersion())
                .put("path", pack.getPath().getFileName().toString())
                .put("timestamp", pack.getTimestamp())
                .put("nightModeAvailable", pack.getMetadata().isNightModeAvailable());
        Optional.ofNullable(pack.getMetadata().getTitle()).ifPresent(title -> json.put("title", title));
        Optional.ofNullable(pack.getMetadata().getDescription()).ifPresent(desc -> json.put("description", desc));
        Optional.ofNullable(pack.getMetadata().getThumbnail()).ifPresent(thumb -> json.put("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(thumb)));
        Optional.ofNullable(pack.getMetadata().getSectorSize()).ifPresent(size -> json.put("sectorSize", size));
        return databaseMetadataService.getPackMetadata(pack.getMetadata().getUuid())
                .map(metadata -> json
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("official", metadata.isOfficial())
                )
                .orElse(json);
    }

}
