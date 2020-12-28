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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LibraryService {

    public static final String LOCAL_LIBRARY_PROP = "studio.library";
    public static final String LOCAL_LIBRARY_PATH = "/.studio/library/";
    public static final String COMMON_KEY_FILE = "/.studio/key";

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
                e.printStackTrace();
                throw new IllegalStateException("Failed to initialize local library");
            }
        }
    }

    public byte[] getCommonKey() throws Exception {
        String commonKeyFilePath = System.getProperty("user.home") + COMMON_KEY_FILE;
        String hexString = Files.readString(Paths.get(commonKeyFilePath)).trim();
        return Hex.decodeHex(hexString);
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
                        .forEach(path -> this.readPackFile(path).ifPresent(
                                meta -> databaseMetadataService.refreshUnofficialMetadata(
                                        new DatabasePackMetadata(
                                                meta.getUuid(),
                                                meta.getTitle(),
                                                meta.getDescription(),
                                                Optional.ofNullable(meta.getThumbnail()).map(thumb -> "data:image/png;base64," + Base64.getEncoder().encodeToString(thumb)).orElse(null),
                                                false
                                        )
                                )
                        ));
            } catch (IOException e) {
                LOGGER.error("Failed to read packs from local library", e);
                throw new RuntimeException(e);
            }

            // List pack files in library folder
            try (Stream<Path> paths = Files.walk(Paths.get(libraryPath()), 1)) {
                return new JsonArray(
                        paths
                                .filter(path -> !path.equals(Paths.get(libraryPath())))
                                .map(path -> this.readPackFile(path).map(
                                        meta -> this.getPackMetadata(meta, path.getFileName().toString())
                                ))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
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

    public Optional<File> getBinaryPackFile(String packPath, Boolean allowEnriched) {
        // Archive format packs must first be converted to binary format
        if (packPath.endsWith(".zip")) {
            try {
                File tmp = File.createTempFile(packPath, ".pack");

                LOGGER.warn("Pack is in archive format. Converting to binary format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.warn("Reading archive format pack");
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                FileInputStream fis = new FileInputStream(libraryPath() + packPath);
                StoryPack storyPack = packReader.read(fis);
                fis.close();

                // Uncompress pack assets
                StoryPack uncompressedPack = storyPack;
                if (PackAssetsCompression.hasCompressedAssets(storyPack)) {
                    LOGGER.warn("Uncompressing pack assets");
                    uncompressedPack = PackAssetsCompression.withUncompressedAssets(storyPack);
                }

                LOGGER.warn("Writing binary format pack");
                BinaryStoryPackWriter packWriter = new BinaryStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(uncompressedPack, fos, allowEnriched);
                fos.close();

                return Optional.of(tmp);
            } catch (IOException e) {
                LOGGER.error("Failed to convert archive format pack to binary format");
                e.printStackTrace();
                return Optional.empty();
            }
        } else if (packPath.endsWith(".pack")) {
            return getRawPackFile(packPath);
        } else {
            try {
                File tmp = File.createTempFile(packPath, ".pack");

                LOGGER.warn("Pack is in FS folder format. Converting to binary format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.warn("Reading FS folder format pack");
                FsStoryPackReader packReader = new FsStoryPackReader(getCommonKey());
                StoryPack storyPack = packReader.read(Paths.get(libraryPath() + packPath));

                // Uncompress pack assets
                StoryPack uncompressedPack = storyPack;
                if (PackAssetsCompression.hasCompressedAssets(storyPack)) {
                    LOGGER.warn("Uncompressing pack assets");
                    uncompressedPack = PackAssetsCompression.withUncompressedAssets(storyPack);
                }

                LOGGER.warn("Writing binary format pack");
                BinaryStoryPackWriter packWriter = new BinaryStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(uncompressedPack, fos, allowEnriched);
                fos.close();

                return Optional.of(tmp);
            } catch (Exception e) {
                LOGGER.error("Failed to convert binary format pack to archive format");
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }

    public Optional<File> getArchivePackFile(String packPath) {
        // Binary format packs must first be converted to archive format
        if (packPath.endsWith(".zip")) {
            return getRawPackFile(packPath);
        } else if (packPath.endsWith(".pack")) {
            try {
                File tmp = File.createTempFile(packPath, ".zip");

                LOGGER.warn("Pack is in binary format. Converting to archive format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.warn("Reading binary format pack");
                BinaryStoryPackReader packReader = new BinaryStoryPackReader();
                FileInputStream fis = new FileInputStream(libraryPath() + packPath);
                StoryPack storyPack = packReader.read(fis);
                fis.close();

                // Compress pack assets
                LOGGER.warn("Compressing pack assets");
                StoryPack compressedPack = PackAssetsCompression.withCompressedAssets(storyPack);

                LOGGER.warn("Writing archive format pack");
                ArchiveStoryPackWriter packWriter = new ArchiveStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(compressedPack, fos);
                fos.close();

                return Optional.of(tmp);
            } catch (IOException e) {
                LOGGER.error("Failed to convert binary format pack to archive format");
                e.printStackTrace();
                return Optional.empty();
            }
        } else {
            try {
                File tmp = File.createTempFile(packPath, ".zip");

                LOGGER.warn("Pack is in FS folder format. Converting to archive format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.warn("Reading FS folder format pack");
                FsStoryPackReader packReader = new FsStoryPackReader(getCommonKey());
                StoryPack storyPack = packReader.read(Paths.get(libraryPath() + packPath));

                // TODO Compress pack assets ?
                /*LOGGER.warn("Compressing pack assets");
                StoryPack compressedPack = PackAssetsCompression.withCompressedAssets(storyPack);*/

                LOGGER.warn("Writing archive format pack");
                ArchiveStoryPackWriter packWriter = new ArchiveStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(storyPack, fos);
                fos.close();

                return Optional.of(tmp);
            } catch (Exception e) {
                LOGGER.error("Failed to convert FS folder format pack to archive format");
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }

    public Optional<File> getFsPackFile(String packPath, String deviceUuid, Boolean allowEnriched) {
        // Archive format packs must first be converted to FS folder format
        if (packPath.endsWith(".zip")) {
            try {
                Path tmp = Files.createTempDirectory(packPath);

                LOGGER.warn("Pack to transfer is in archive format. Converting to FS folder format and storing in temporary folder: " + tmp.toAbsolutePath().toString());

                LOGGER.warn("Reading archive format pack");
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                FileInputStream fis = new FileInputStream(libraryPath() + packPath);
                StoryPack storyPack = packReader.read(fis);
                fis.close();

                // Prepare assets (RLE-encoded BMP, audio must already be MP3)
                StoryPack packWithPreparedAssets = PackAssetsCompression.withPreparedAssetsFirmware2dot4(storyPack);

                LOGGER.warn("Writing FS folder format pack");
                FsStoryPackWriter writer = new FsStoryPackWriter(Hex.decodeHex(deviceUuid), getCommonKey());
                Path folderPath = writer.write(packWithPreparedAssets, tmp);

                return Optional.of(folderPath.toFile());
            } catch (Exception e) {
                LOGGER.error("Failed to convert archive format pack to binary format");
                e.printStackTrace();
                return Optional.empty();
            }
        } else if (packPath.endsWith(".pack")) {
            // FIXME Support converting from binary pack to FS folder pack
            return Optional.empty();
        } else {
            return getRawPackFile(packPath);
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
                e.printStackTrace();
                return false;
            }
        }
    }

    public String libraryPath() {
        // Path may be overridden by system property `studio.library`
        return System.getProperty(LOCAL_LIBRARY_PROP, System.getProperty("user.home") + LOCAL_LIBRARY_PATH);
    }

    private Optional<StoryPackMetadata> readPackFile(Path path) {
        LOGGER.debug("Reading pack file: " + path.toString());
        // Handle both binary and archive file formats
        if (path.toString().endsWith(".zip")) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                LOGGER.debug("Reading archive pack metadata.");
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                return Optional.ofNullable(packReader.readMetadata(fis));
            } catch (IOException e) {
                LOGGER.error("Failed to read archive-format pack " + path.toString() + " from local library", e);
                e.printStackTrace();
                return Optional.empty();
            }
        } else if (path.toString().endsWith(".pack")) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                LOGGER.debug("Reading binary pack metadata.");
                BinaryStoryPackReader packReader = new BinaryStoryPackReader();
                Optional<StoryPackMetadata> metadata = Optional.of(packReader.readMetadata(fis));
                metadata.map(meta -> {
                    int packSectorSize = (int)Math.ceil((double)path.toFile().length() / 512d);
                    meta.setSectorSize(packSectorSize);
                    return meta;
                });
                return metadata;
            } catch (IOException e) {
                LOGGER.error("Failed to read binary-format pack " + path.toString() + " from local library", e);
                e.printStackTrace();
                return Optional.empty();
            }
        } else if (Files.isDirectory(path)) {
            try {
                LOGGER.debug("Reading FS folder pack metadata.");
                FsStoryPackReader packReader = new FsStoryPackReader(getCommonKey());
                Optional<StoryPackMetadata> metadata = Optional.of(packReader.readMetadata(path));
                metadata.map(meta -> {
                    int packSectorSize = (int)Math.ceil((double)path.toFile().length() / 512d);
                    meta.setSectorSize(packSectorSize);
                    return meta;
                });
                return metadata;
            } catch (Exception e) {
                LOGGER.error("Failed to read FS folder format pack " + path.toString() + " from local library", e);
                e.printStackTrace();
                return Optional.empty();
            }
        }

        // Ignore other files
        return Optional.empty();
    }

    private JsonObject getPackMetadata(StoryPackMetadata packMetadata, String path) {
        JsonObject json = new JsonObject()
                .put("format", packMetadata.getFormat())
                .put("uuid", packMetadata.getUuid())
                .put("version", packMetadata.getVersion())
                .put("path", path);
        Optional.ofNullable(packMetadata.getTitle()).ifPresent(title -> json.put("title", title));
        Optional.ofNullable(packMetadata.getDescription()).ifPresent(desc -> json.put("description", desc));
        Optional.ofNullable(packMetadata.getThumbnail()).ifPresent(thumb -> json.put("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(thumb)));
        Optional.ofNullable(packMetadata.getSectorSize()).ifPresent(size -> json.put("sectorSize", size));
        return databaseMetadataService.getPackMetadata(packMetadata.getUuid())
                .map(metadata -> json
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("official", metadata.isOfficial())
                )
                .orElse(json);
    }

}
