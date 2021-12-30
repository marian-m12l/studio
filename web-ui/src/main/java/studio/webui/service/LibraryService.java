/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.core.v1.Constants;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.archive.ArchiveStoryPackReader;
import studio.core.v1.reader.binary.BinaryStoryPackReader;
import studio.core.v1.reader.fs.FsStoryPackReader;
import studio.core.v1.utils.PackAssetsCompression;
import studio.core.v1.writer.archive.ArchiveStoryPackWriter;
import studio.core.v1.writer.binary.BinaryStoryPackWriter;
import studio.core.v1.writer.fs.FsStoryPackWriter;
import studio.driver.fs.FileUtils;
import studio.metadata.DatabaseMetadataService;
import studio.metadata.DatabasePackMetadata;
import studio.webui.model.LibraryPack;

public class LibraryService {

    public static final String LOCAL_LIBRARY_PROP = "studio.library";
    public static final String LOCAL_LIBRARY_PATH = "/.studio/library/";
    public static final String TMP_DIR_PROP = "studio.tmpdir";
    public static final String TMP_DIR_PATH = "/.studio/tmp/";

    private final Logger LOGGER = LoggerFactory.getLogger(LibraryService.class);

    private final DatabaseMetadataService databaseMetadataService;

    private static final Path libraryPath = libraryPath();
    private static final Path tmpDirPath = tmpDirPath();
    
    public LibraryService(DatabaseMetadataService databaseMetadataService) {
        this.databaseMetadataService = databaseMetadataService;

        // Create the local library folder if needed
        if (Files.notExists(libraryPath) || !Files.isDirectory(libraryPath)) {
            try {
                Files.createDirectories(libraryPath);
            } catch (IOException e) {
                LOGGER.error("Failed to initialize local library", e);
                throw new IllegalStateException("Failed to initialize local library");
            }
        }

        // Create the temp folder if needed
        if (Files.notExists(tmpDirPath) || !Files.isDirectory(tmpDirPath)) {
            try {
                Files.createDirectories(tmpDirPath);
            } catch (IOException e) {
                LOGGER.error("Failed to initialize temp folder", e);
                throw new IllegalStateException("Failed to initialize temp folder");
            }
        }
    }

    public JsonObject libraryInfos() {
        return new JsonObject()
                .put("path", libraryPath.toString());
    }

    public JsonArray packs() {
        // Check that local library folder exists
        if (Files.notExists(libraryPath) || !Files.isDirectory(libraryPath)) {
            return new JsonArray();
        } else {
            // First, refresh unofficial database with metadata from archive packs
            try (Stream<Path> paths = Files.walk(libraryPath, 1)) {
                paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".zip"))
                        .map(this::readPackFile)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        // Group packs by UUID
                        .collect(Collectors.groupingBy(p -> p.getMetadata().getUuid()))
                        .entrySet()
                        .forEach(entry -> {
                            List<LibraryPack> packs = entry.getValue();
                            packs.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                            LOGGER.debug("Refreshing metadata for pack `" + entry.getKey() + "` from file `" + packs.get(0).getPath() + "`");
                            this.readPackFile(packs.get(0).getPath()).ifPresent(
                                    meta -> databaseMetadataService.refreshUnofficialMetadata(
                                            new DatabasePackMetadata(
                                                    meta.getMetadata().getUuid(),
                                                    meta.getMetadata().getTitle(),
                                                    meta.getMetadata().getDescription(),
                                                    Optional.ofNullable(meta.getMetadata().getThumbnail()).map(thumb -> "data:image/png;base64," + Base64.getEncoder().encodeToString(thumb)).orElse(null),
                                                    false
                                            )
                                    )
                            );
                        });
            } catch (IOException e) {
                LOGGER.error("Failed to read packs from local library", e);
                throw new RuntimeException(e);
            }

            // List pack files in library folder
            try (Stream<Path> paths = Files.walk(libraryPath, 1)) {
                return new JsonArray(
                        paths
                                .filter(path -> !path.equals(libraryPath))
                                .map(this::readPackFile)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                // Group packs by UUID
                                .collect(Collectors.groupingBy(p -> p.getMetadata().getUuid()))
                                .entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> new JsonArray(
                                                entry.getValue().stream()
                                                        // Sort packs by timestamp descending
                                                        .sorted((a,b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                                                        .map(this::getPackMetadata)
                                                        .collect(Collectors.toList())
                                        )
                                ))
                                .entrySet().stream()
                                .map(entry -> new JsonObject().put("uuid", entry.getKey()).put("packs", entry.getValue()))
                                .collect(Collectors.toList())
                );
            } catch (IOException e) {
                LOGGER.error("Failed to read packs from local library", e);
                throw new RuntimeException(e);
            }
        }
    }

    public Optional<Path> getRawPackFile(String packPath) {
        return Optional.of(libraryPath.resolve(packPath));
    }

    private void assertFormat(String outputFormat) {
        String msg = "Pack is already in " + outputFormat + " format";
        LOGGER.error(msg);
        throw new RuntimeException(msg);
    }
    
    public Optional<Path> addConvertedRawPackFile(String packFile, Boolean allowEnriched) {
        String outputFormat = Constants.PACK_FORMAT_RAW;
        if (packFile.endsWith(".pack")) {
            assertFormat(outputFormat);
        }
        // expected input format type
        String inputFormat = packFile.endsWith(".zip") ? Constants.PACK_FORMAT_ARCHIVE : Constants.PACK_FORMAT_FS;
        LOGGER.info("Pack is in " + inputFormat + " format. Converting to " + outputFormat + " format");
        try {
            // Packs must first be converted to raw format
            StoryPack storyPack;
            Path packPath = libraryPath.resolve(packFile);
            LOGGER.info("Reading " + inputFormat + " format pack");
            if (packFile.endsWith(".zip")) {
                storyPack = new ArchiveStoryPackReader().read(packPath);
            } else {
                storyPack = new FsStoryPackReader().read(packPath);
            }
            // Uncompress pack assets
            StoryPack uncompressedPack = storyPack;
            if (PackAssetsCompression.hasCompressedAssets(storyPack)) {
                LOGGER.info("Uncompressing pack assets");
                uncompressedPack = PackAssetsCompression.withUncompressedAssets(storyPack);
            }

            Path tmp = createTempFile(packFile, ".pack");
            LOGGER.info("Writing " + outputFormat + " format pack, using temporary file: " + tmp);
            try(OutputStream os = Files.newOutputStream(tmp)) {
                new BinaryStoryPackWriter().write(uncompressedPack, os, allowEnriched);
            }

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
        String outputFormat = Constants.PACK_FORMAT_ARCHIVE;
        if (packFile.endsWith(".zip")) {
            assertFormat(outputFormat);
        } 
        // expected input format type
        String inputFormat = packFile.endsWith(".pack") ? Constants.PACK_FORMAT_RAW : Constants.PACK_FORMAT_FS;
        LOGGER.info("Pack is in " + inputFormat + " format. Converting to " + outputFormat + " format");
        try {
            // Packs must first be converted to raw format
            StoryPack storyPack;
            Path packPath = libraryPath.resolve(packFile);
            LOGGER.info("Reading " + inputFormat + " format pack");
            if (packFile.endsWith(".pack")) {
                try(InputStream is = Files.newInputStream(packPath)) {
                    storyPack = new BinaryStoryPackReader().read(is);
                }
                // Compress pack assets
                LOGGER.info("Compressing pack assets");
                storyPack = PackAssetsCompression.withCompressedAssets(storyPack);
            } else {
                storyPack = new FsStoryPackReader().read(packPath);
               // No need to compress pack assets
            }

            Path tmp = createTempFile(packFile, ".zip");
            LOGGER.info("Writing " + outputFormat + " format pack, using temporary file: " + tmp);
            new ArchiveStoryPackWriter().write(storyPack, tmp);

            String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".zip";
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

    public Optional<Path> addConvertedFsPackFile(String packFile, Boolean allowEnriched) {
        String outputFormat = Constants.PACK_FORMAT_FS;
        if (!packFile.endsWith(".zip") && !packFile.endsWith(".pack")) {
            assertFormat(outputFormat);
        } 
        // expected input format type
        String inputFormat = packFile.endsWith(".zip") ? Constants.PACK_FORMAT_ARCHIVE : Constants.PACK_FORMAT_RAW;
        LOGGER.info("Pack is in " + inputFormat + " format. Converting to " + outputFormat + " format");
        try {
            // Packs must first be converted to raw format
            StoryPack storyPack;
            Path packPath = libraryPath.resolve(packFile);
            LOGGER.info("Reading " + inputFormat + " format pack");
            if (packFile.endsWith(".zip")) {
               storyPack = new ArchiveStoryPackReader().read(packPath);
            } else {
                try(InputStream is = Files.newInputStream(packPath)) {
                    storyPack = new BinaryStoryPackReader().read(is);
                }
            }
            // Prepare assets (RLE-encoded BMP, audio must already be MP3)
            LOGGER.info("Converting assets if necessary");
            storyPack = PackAssetsCompression.withPreparedAssetsFirmware2dot4(storyPack);

            Path tmp = createTempDirectory(packFile);
            LOGGER.info("Writing " + outputFormat + " format pack, using temporary folder: " + tmp);
            // should we not keep uuid instead ?
            Path tmpPath = new FsStoryPackWriter().write(storyPack, tmp);

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
        if (Files.notExists(libraryPath) || !Files.isDirectory(libraryPath)) {
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
        // Path may be overridden by system property `studio.library`
        return Path.of(System.getProperty(LOCAL_LIBRARY_PROP, System.getProperty("user.home") + LOCAL_LIBRARY_PATH));
    }

    private static Path tmpDirPath() {
        // Path may be overridden by system property `studio.tmpdir`
        return Path.of(System.getProperty(TMP_DIR_PROP, System.getProperty("user.home") + TMP_DIR_PATH) );
    }

    private Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(tmpDirPath, prefix, suffix);
    }

    private Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(tmpDirPath, prefix);
    }

    private Optional<LibraryPack> readPackFile(Path path) {
        LOGGER.debug("Reading pack file: " + path);
        // Handle all file formats
        if (path.toString().endsWith(".zip")) {
            try {
                LOGGER.debug("Reading archive pack metadata.");
                StoryPackMetadata meta = new ArchiveStoryPackReader().readMetadata(path);
                if (meta != null) {
                    return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis() , meta));
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read archive-format pack " + path + " from local library", e);
            }
        } else if (path.toString().endsWith(".pack")) {
            try (InputStream is = Files.newInputStream(path)) {
                LOGGER.debug("Reading raw pack metadata.");
                StoryPackMetadata meta = new BinaryStoryPackReader().readMetadata(is);
                if (meta != null) {
                    meta.setSectorSize((int)Math.ceil(Files.size(path) / 512d));
                    return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis() , meta));
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read raw format pack " + path + " from local library", e);
            }
        } else if (Files.isDirectory(path)) {
            try {
                LOGGER.debug("Reading FS pack metadata.");
                StoryPackMetadata meta = new FsStoryPackReader().readMetadata(path);
                if (meta != null) {
                    meta.setSectorSize((int)Math.ceil(Files.size(path) / 512d));
                    return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis() , meta));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to read FS format pack " + path + " from local library", e);
            }
        }
        // Ignore other files OR read error
        return Optional.empty();
    }

    private JsonObject getPackMetadata(LibraryPack pack) {
        JsonObject json = new JsonObject()
                .put("format", pack.getMetadata().getFormat())
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
