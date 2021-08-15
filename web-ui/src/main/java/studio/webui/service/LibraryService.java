/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.archive.ArchiveStoryPackReader;
import studio.core.v1.reader.binary.BinaryStoryPackReader;
import studio.core.v1.reader.fs.FsStoryPackReader;
import studio.core.v1.utils.PackAssetsCompression;
import studio.core.v1.writer.archive.ArchiveStoryPackWriter;
import studio.core.v1.writer.binary.BinaryStoryPackWriter;
import studio.core.v1.writer.fs.FsStoryPackWriter;
import studio.metadata.DatabaseMetadataService;
import studio.metadata.DatabasePackMetadata;
import studio.webui.model.LibraryPack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LibraryService {

    public static final String LOCAL_LIBRARY_PROP = "studio.library";
    public static final String LOCAL_LIBRARY_PATH = "/.studio/library/";
    public static final String TMP_DIR_PROP = "studio.tmpdir";
    public static final String TMP_DIR_PATH = "/.studio/tmp/";

    private final Logger LOGGER = LoggerFactory.getLogger(LibraryService.class);

    private final DatabaseMetadataService databaseMetadataService;

    public LibraryService(DatabaseMetadataService databaseMetadataService) {
        this.databaseMetadataService = databaseMetadataService;

        // Create the local library folder if needed
        File libraryFolder = new File(libraryPath());
        if (!libraryFolder.exists() || !libraryFolder.isDirectory()) {
            try {
                Files.createDirectories(Paths.get(libraryPath()));
            } catch (IOException e) {
                LOGGER.error("Failed to initialize local library", e);
                throw new IllegalStateException("Failed to initialize local library");
            }
        }

        // Create the temp folder if needed
        File tmpFolder = new File(tmpDirPath());
        if (!tmpFolder.exists() || !tmpFolder.isDirectory()) {
            try {
                Files.createDirectories(Paths.get(tmpDirPath()));
            } catch (IOException e) {
                LOGGER.error("Failed to initialize temp folder", e);
                throw new IllegalStateException("Failed to initialize temp folder");
            }
        }
    }

    public JsonObject libraryInfos() {
        return new JsonObject()
                .put("path", libraryPath());
    }

    public JsonArray packs() {
        // Check that local library folder exists
        File libraryFolder = new File(libraryPath());
        if (!libraryFolder.exists() || !libraryFolder.isDirectory()) {
            return new JsonArray();
        } else {
            // First, refresh unofficial database with metadata from archive packs
            try (Stream<Path> paths = Files.walk(Paths.get(libraryPath()), 1)) {
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
            try (Stream<Path> paths = Files.walk(Paths.get(libraryPath()), 1)) {
                return new JsonArray(
                        paths
                                .filter(path -> !path.equals(Paths.get(libraryPath())))
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

    public Optional<File> getRawPackFile(String packPath) {
        return Optional.of(new File(libraryPath() + packPath));
    }

    public Optional<Path> addConvertedRawPackFile(String packPath, Boolean allowEnriched) {
        // Archive format packs must first be converted to raw format
        if (packPath.endsWith(".zip")) {
            try {
                File tmp = createTempFile(packPath, ".pack").toFile();

                LOGGER.info("Pack is in archive format. Converting to raw format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.info("Reading archive format pack");
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                FileInputStream fis = new FileInputStream(libraryPath() + packPath);
                StoryPack storyPack = packReader.read(fis);
                fis.close();

                // Uncompress pack assets
                StoryPack uncompressedPack = storyPack;
                if (PackAssetsCompression.hasCompressedAssets(storyPack)) {
                    LOGGER.info("Uncompressing pack assets");
                    uncompressedPack = PackAssetsCompression.withUncompressedAssets(storyPack);
                }

                LOGGER.info("Writing raw format pack");
                BinaryStoryPackWriter packWriter = new BinaryStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(uncompressedPack, fos, allowEnriched);
                fos.close();

                String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".pack";
                Path destinationPath = Paths.get(libraryPath() + destinationFileName);
                LOGGER.info("Moving raw format pack into local library: " + destinationPath);
                Files.move(tmp.toPath(), destinationPath);

                return Optional.of(Paths.get(destinationFileName));
            } catch (Exception e) {
                LOGGER.error("Failed to convert archive format pack to raw format", e);
                throw new RuntimeException("Failed to convert archive format pack to raw format", e);
            }
        } else if (packPath.endsWith(".pack")) {
            LOGGER.error("Pack is already in raw format");
            throw new RuntimeException("Pack is already in raw format");
        } else {
            try {
                File tmp = createTempFile(packPath, ".pack").toFile();

                LOGGER.info("Pack is in FS format. Converting to raw format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.info("Reading FS format pack");
                FsStoryPackReader packReader = new FsStoryPackReader();
                StoryPack storyPack = packReader.read(Paths.get(libraryPath() + packPath));

                // Uncompress pack assets
                StoryPack uncompressedPack = storyPack;
                if (PackAssetsCompression.hasCompressedAssets(storyPack)) {
                    LOGGER.info("Uncompressing pack assets");
                    uncompressedPack = PackAssetsCompression.withUncompressedAssets(storyPack);
                }

                LOGGER.info("Writing raw format pack");
                BinaryStoryPackWriter packWriter = new BinaryStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(uncompressedPack, fos, allowEnriched);
                fos.close();

                String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".pack";
                Path destinationPath = Paths.get(libraryPath() + destinationFileName);
                LOGGER.info("Moving raw format pack into local library: " + destinationPath);
                Files.move(tmp.toPath(), destinationPath);

                return Optional.of(Paths.get(destinationFileName));
            } catch (Exception e) {
                LOGGER.error("Failed to convert FS format pack to raw format", e);
                throw new RuntimeException("Failed to convert FS format pack to raw format", e);
            }
        }
    }

    public Optional<Path> addConvertedArchivePackFile(String packPath) {
        // Binary format packs must first be converted to archive format
        if (packPath.endsWith(".zip")) {
            LOGGER.error("Pack is already in archive format");
            throw new RuntimeException("Pack is already in archive format");
        } else if (packPath.endsWith(".pack")) {
            try {
                File tmp = createTempFile(packPath, ".zip").toFile();

                LOGGER.info("Pack is in raw format. Converting to archive format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.info("Reading raw format pack");
                BinaryStoryPackReader packReader = new BinaryStoryPackReader();
                FileInputStream fis = new FileInputStream(libraryPath() + packPath);
                StoryPack storyPack = packReader.read(fis);
                fis.close();

                // Compress pack assets
                LOGGER.info("Compressing pack assets");
                StoryPack compressedPack = PackAssetsCompression.withCompressedAssets(storyPack);

                LOGGER.info("Writing archive format pack");
                ArchiveStoryPackWriter packWriter = new ArchiveStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(compressedPack, fos);
                fos.close();

                String destinationFileName = compressedPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".zip";
                Path destinationPath = Paths.get(libraryPath() + destinationFileName);
                LOGGER.info("Moving archive format pack into local library: " + destinationPath);
                Files.move(tmp.toPath(), destinationPath);

                return Optional.of(Paths.get(destinationFileName));
            } catch (Exception e) {
                LOGGER.error("Failed to convert raw format pack to archive format", e);
                throw new RuntimeException("Failed to convert raw format pack to archive format", e);
            }
        } else {
            try {
                File tmp = createTempFile(packPath, ".zip").toFile();

                LOGGER.info("Pack is in FS format. Converting to archive format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.info("Reading FS format pack");
                FsStoryPackReader packReader = new FsStoryPackReader();
                StoryPack storyPack = packReader.read(Paths.get(libraryPath() + packPath));

                // No need to compress pack assets

                LOGGER.info("Writing archive format pack");
                ArchiveStoryPackWriter packWriter = new ArchiveStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(storyPack, fos);
                fos.close();

                String destinationFileName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis() + ".zip";
                Path destinationPath = Paths.get(libraryPath() + destinationFileName);
                LOGGER.info("Moving archive format pack into local library: " + destinationPath);
                Files.move(tmp.toPath(), destinationPath);

                return Optional.of(Paths.get(destinationFileName));
            } catch (Exception e) {
                LOGGER.error("Failed to convert FS format pack to archive format", e);
                throw new RuntimeException("Failed to convert FS format pack to archive format", e);
            }
        }
    }

    public Optional<Path> addConvertedFsPackFile(String packPath, Boolean allowEnriched) {
        // Archive format packs must first be converted to FS format
        if (packPath.endsWith(".zip")) {
            try {
                Path tmp = createTempDirectory(packPath);

                LOGGER.info("Pack to transfer is in archive format. Converting to FS format and storing in temporary folder: " + tmp.toAbsolutePath().toString());

                LOGGER.info("Reading archive format pack");
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                FileInputStream fis = new FileInputStream(libraryPath() + packPath);
                StoryPack storyPack = packReader.read(fis);
                fis.close();

                // Prepare assets (RLE-encoded BMP, audio must already be MP3)
                LOGGER.info("Converting assets if necessary");
                StoryPack packWithPreparedAssets = PackAssetsCompression.withPreparedAssetsFirmware2dot4(storyPack);

                LOGGER.info("Writing FS format pack");
                FsStoryPackWriter writer = new FsStoryPackWriter();
                Path folderPath = writer.write(packWithPreparedAssets, tmp);

                String destinationFolder = packWithPreparedAssets.getUuid() + ".converted_" + System.currentTimeMillis();
                Path destinationPath = Paths.get(libraryPath() + destinationFolder);
                LOGGER.info("Moving FS format pack into local library: " + destinationPath);
                Files.move(folderPath, destinationPath);

                return Optional.of(Paths.get(destinationFolder));
            } catch (Exception e) {
                LOGGER.error("Failed to convert archive format pack to FS format", e);
                throw new RuntimeException("Failed to convert archive format pack to FS format", e);
            }
        } else if (packPath.endsWith(".pack")) {
            try {
                Path tmp = createTempDirectory(packPath);

                LOGGER.info("Pack is in raw format. Converting to FS format and storing in temporary folder: " + tmp.toAbsolutePath().toString());

                LOGGER.info("Reading raw format pack");
                BinaryStoryPackReader packReader = new BinaryStoryPackReader();
                FileInputStream fis = new FileInputStream(libraryPath() + packPath);
                StoryPack storyPack = packReader.read(fis);
                fis.close();

                // Prepare assets (RLE-encoded BMP, audio must already be MP3)
                LOGGER.info("Converting assets if necessary");
                StoryPack packWithPreparedAssets = PackAssetsCompression.withPreparedAssetsFirmware2dot4(storyPack);

                LOGGER.info("Writing FS format pack");
                FsStoryPackWriter writer = new FsStoryPackWriter();
                Path folderPath = writer.write(packWithPreparedAssets, tmp);

                String destinationFolder = packWithPreparedAssets.getUuid() + ".converted_" + System.currentTimeMillis();
                Path destinationPath = Paths.get(libraryPath() + destinationFolder);
                LOGGER.info("Moving FS format pack into local library: " + destinationPath);
                Files.move(folderPath, destinationPath);

                return Optional.of(Paths.get(destinationFolder));
            } catch (Exception e) {
                LOGGER.error("Failed to convert raw format pack to FS format", e);
                throw new RuntimeException("Failed to convert raw format pack to FS format", e);
            }
        } else {
            LOGGER.error("Pack is already in FS format");
            throw new RuntimeException("Pack is already in FS format");
        }
    }

    public boolean addPackFile(String destPath, String uploadedFilePath) {
        try {
            // Copy temporary file to local library
            File src = new File(uploadedFilePath);
            File dest = new File(libraryPath() + destPath);
            if (dest.exists()) {
                boolean deleted = dest.delete();
                // Handle failure
                if (!deleted) {
                    return false;
                }
            }
            FileUtils.moveFile(src, dest);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to add pack to local library", e);
            throw new RuntimeException(e);
        }
    }

    public boolean deletePack(String packPath) {
        File libraryFolder = new File(libraryPath());
        if (!libraryFolder.exists() || !libraryFolder.isDirectory()) {
            return false;
        } else {
            try {
                File packFile = new File(libraryPath() + packPath);
                if (packFile.exists()) {
                    FileUtils.forceDelete(packFile);
                    return true;
                } else {
                    LOGGER.error("Cannot remove pack from library because it is not in the folder");
                    return false;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to remove pack from library", e);
                return false;
            }
        }
    }

    public String libraryPath() {
        // Path may be overridden by system property `studio.library`
        return System.getProperty(LOCAL_LIBRARY_PROP, System.getProperty("user.home") + LOCAL_LIBRARY_PATH);
    }

    private String tmpDirPath() {
        // Path may be overridden by system property `studio.tmpdir`
        return System.getProperty(TMP_DIR_PROP, System.getProperty("user.home") + TMP_DIR_PATH);
    }

    private Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(Paths.get(tmpDirPath()), prefix, suffix);
    }

    private Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(Paths.get(tmpDirPath()), prefix);
    }

    private Optional<LibraryPack> readPackFile(Path path) {
        LOGGER.debug("Reading pack file: " + path.toString());
        // Handle all file formats
        if (path.toString().endsWith(".zip")) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                LOGGER.debug("Reading archive pack metadata.");
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                StoryPackMetadata meta = packReader.readMetadata(fis);
                if (meta != null) {
                    return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis() , meta));
                }
                return Optional.empty();
            } catch (IOException e) {
                LOGGER.error("Failed to read archive-format pack " + path.toString() + " from local library", e);
                return Optional.empty();
            }
        } else if (path.toString().endsWith(".pack")) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                LOGGER.debug("Reading raw pack metadata.");
                BinaryStoryPackReader packReader = new BinaryStoryPackReader();
                StoryPackMetadata meta = packReader.readMetadata(fis);
                if (meta != null) {
                    int packSectorSize = (int)Math.ceil((double)path.toFile().length() / 512d);
                    meta.setSectorSize(packSectorSize);
                    return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis() , meta));
                }
                return Optional.empty();
            } catch (IOException e) {
                LOGGER.error("Failed to read raw format pack " + path.toString() + " from local library", e);
                return Optional.empty();
            }
        } else if (Files.isDirectory(path)) {
            try {
                LOGGER.debug("Reading FS pack metadata.");
                FsStoryPackReader packReader = new FsStoryPackReader();
                StoryPackMetadata meta = packReader.readMetadata(path);
                if (meta != null) {
                    int packSectorSize = (int)Math.ceil((double)path.toFile().length() / 512d);
                    meta.setSectorSize(packSectorSize);
                    return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis() , meta));
                }
                return Optional.empty();
            } catch (Exception e) {
                LOGGER.error("Failed to read FS format pack " + path.toString() + " from local library", e);
                return Optional.empty();
            }
        }

        // Ignore other files
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
