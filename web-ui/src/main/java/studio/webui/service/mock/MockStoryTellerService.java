/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.binary.BinaryStoryPackReader;
import studio.metadata.DatabaseMetadataService;
import studio.webui.service.IStoryTellerService;

public class MockStoryTellerService implements IStoryTellerService {

    public static final String MOCKED_DEVICE_PATH = "/.studio/device/";

    private static final int BUFFER_SIZE = 1024 * 1024 * 10;

    private static final ScheduledThreadPoolExecutor THREAD_POOL = new ScheduledThreadPoolExecutor(2);

    private final Logger LOGGER = LoggerFactory.getLogger(MockStoryTellerService.class);

    private final EventBus eventBus;

    private final DatabaseMetadataService databaseMetadataService;

    public MockStoryTellerService(EventBus eventBus, DatabaseMetadataService databaseMetadataService) {
        this.eventBus = eventBus;
        this.databaseMetadataService = databaseMetadataService;

        LOGGER.info("Setting up mocked story teller service");

        // Create the mocked device folder if needed
        Path libraryFolder = devicePath();
        if (Files.notExists(libraryFolder) || !Files.isDirectory(libraryFolder)) {
            try {
                Files.createDirectories(libraryFolder);
            } catch (IOException e) {
                LOGGER.error("Failed to initialize mocked device", e);
                throw new IllegalStateException("Failed to initialize mocked device");
            }
        }
    }

    private static Path devicePath() {
        return Path.of(System.getProperty("user.home") + MOCKED_DEVICE_PATH);
    }

    public CompletableFuture<Optional<JsonObject>> deviceInfos() {
        try(Stream<Path> paths = Files.list(devicePath())) {
            long files = paths.count();
            return CompletableFuture.completedFuture(
                    Optional.of(new JsonObject()
                            .put("uuid", "mocked-device")
                            .put("serial", "mocked-serial")
                            .put("firmware", "mocked-version")
                            .put("storage", new JsonObject()
                                    .put("size", files)
                                    .put("free", 0)
                                    .put("taken", files)
                            )
                            .put("error", false)
                            .put("driver", "raw") // Simulate raw only
                    )
            );
        } catch (IOException e) {
            LOGGER.error("Failed to initialize mocked device", e);
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<JsonArray> packs() {
        // Check that mocked device folder exists
        Path deviceFolder = devicePath();
        if (Files.notExists(deviceFolder) || !Files.isDirectory(deviceFolder)) {
            return CompletableFuture.completedFuture(new JsonArray());
        } else {
            return readPackIndex(deviceFolder).thenApply(packs -> new JsonArray( //
                    packs.stream().map(this::getPackMetadata).collect(Collectors.toList()) //
            ));
        }
    }

    private CompletableFuture<List<StoryPackMetadata>> readPackIndex(Path deviceFolder) {
        // List binary pack files in mocked device folder
        try (Stream<Path> paths = Files.walk(deviceFolder).filter(Files::isRegularFile)) {
            return CompletableFuture.completedFuture( //
                    paths.map(this::readBinaryPackFile) //
                    .filter(Optional::isPresent) //
                    .map(Optional::get) //
                    .collect(Collectors.toList()) //
            );
        } catch (IOException e) {
            LOGGER.error("Failed to read packs from mocked device", e);
            throw new RuntimeException(e);
        }
    }
    
    private Optional<StoryPackMetadata> readBinaryPackFile(Path path) {
        LOGGER.debug("Reading pack file: " + path.toString());
        // Handle only binary file format
        if (path.toString().endsWith(".pack")) {
            try {
                LOGGER.debug("Reading binary pack metadata.");
                StoryPackMetadata meta = new BinaryStoryPackReader().readMetadata(path);
                if (meta != null) {
                    meta.setSectorSize((int)Math.ceil(Files.size(path) / 512d));
                    //return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis() , meta));
                    return Optional.of(meta);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read binary-format pack " + path.toString() + " from mocked device", e);
                return Optional.empty();
            }
        } else if (path.toString().endsWith(".zip")) {
            LOGGER.error("Mocked device should not contain archive-format packs");
            return Optional.empty();
        }

        // Ignore other files
        return Optional.empty();
    }

    public CompletableFuture<Optional<String>> addPack(String uuid, Path packFile) {
        // Check that mocked device folder exists
        Path deviceFolder = devicePath();
        if (Files.notExists(deviceFolder) || !Files.isDirectory(deviceFolder)) {
            return CompletableFuture.completedFuture(Optional.empty());
        } else {
            String transferId = UUID.randomUUID().toString();
            // Perform transfer asynchronously, and send events on eventbus to monitor progress and end of transfer
            THREAD_POOL.schedule( () -> {
                    // Make sure the device does not already contain this pack
                    Path destFile = deviceFolder.resolve(uuid + ".pack");
                    if (Files.exists(destFile)) {
                        LOGGER.error("Cannot add pack to mocked device because the folder already contains this pack");
                        eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                        return;
                    }
                    try (InputStream input = Files.newInputStream(packFile);
                            OutputStream output = Files.newOutputStream(destFile)) {
                        long fileSize = Files.size(packFile);
                        final byte[] buffer = new byte[BUFFER_SIZE];
                        long count = 0;
                        int n = 0;
                        while ((n = input.read(buffer)) != -1) {
                            output.write(buffer, 0, n);
                            count += n;
                            // Send events on eventbus to monitor progress
                            double p = count / (double) fileSize;
                            LOGGER.debug("Pack copy progress... " + count + " / " + fileSize + " (" + p + ")");
                            eventBus.send("storyteller.transfer."+transferId+".progress", new JsonObject().put("progress", p));
                        }
                        // Send event on eventbus to signal end of transfer
                        eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", true));
                    } catch (IOException e) {
                        LOGGER.error("Failed to add pack to mocked device", e);
                        // Send event on eventbus to signal transfer failure
                        eventBus.send("storyteller.transfer." + transferId + ".done", new JsonObject().put("success", false));
                    }
                }, 1, TimeUnit.SECONDS);
            return CompletableFuture.completedFuture(Optional.of(transferId));
        }
    }

    public CompletableFuture<Boolean> deletePack(String uuid) {
        // Check that mocked device folder exists
        Path deviceFolder = devicePath();
        if (Files.notExists(deviceFolder) || !Files.isDirectory(deviceFolder)) {
            return CompletableFuture.completedFuture(false);
        } else {
            try {
                Path packFile = deviceFolder.resolve(uuid + ".pack");
                if (Files.exists(packFile)) {
                    Files.delete(packFile);
                    return CompletableFuture.completedFuture(true);
                } else {
                    LOGGER.error("Cannot remove pack from mocked device because it is not in the folder");
                    return CompletableFuture.completedFuture(false);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to remove pack from mocked device", e);
                return CompletableFuture.completedFuture(false);
            }
        }
    }

    public CompletableFuture<Boolean> reorderPacks(List<String> uuids) {
        // Not supported
        LOGGER.warn("Not supported : reorderPacks");
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Optional<String>> extractPack(String uuid, Path destFile) {
        // Check that mocked device folder exists
        Path deviceFolder = devicePath();
        if (Files.notExists(deviceFolder) || !Files.isDirectory(deviceFolder)) {
            return CompletableFuture.completedFuture(Optional.empty());
        } else {
            String transferId = UUID.randomUUID().toString();
            // Perform transfer asynchronously, and send events on eventbus to monitor progress and end of transfer
            THREAD_POOL.schedule( () -> {
                    Path packFile = deviceFolder.resolve(uuid + ".pack");
                    if (Files.exists(packFile)) {
                        // Check that the destination is available
                        if (Files.exists(destFile)) {
                            LOGGER.error("Cannot extract pack from mocked device because the destination file already exists");
                            eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                            return;
                        }
                        try (InputStream input = Files.newInputStream(packFile);
                                OutputStream output = Files.newOutputStream(destFile)) {
                            long fileSize = Files.size(packFile);
                            final byte[] buffer = new byte[BUFFER_SIZE];
                            long count = 0;
                            int n = 0;
                            while ((n = input.read(buffer)) != -1) {
                                output.write(buffer, 0, n);
                                count += n;
                                // Send events on eventbus to monitor progress
                                double p = count / (double) fileSize;
                                LOGGER.debug("Pack copy progress... " + count + " / " + fileSize + " (" + p + ")");
                                eventBus.send("storyteller.transfer."+transferId+".progress", new JsonObject().put("progress", p));
                            }
                            // Send event on eventbus to signal end of transfer
                            eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", true));
                        } catch (IOException e) {
                            LOGGER.error("Failed to extract pack from mocked device", e);
                            // Send event on eventbus to signal transfer failure
                            eventBus.send("storyteller.transfer." + transferId + ".done", new JsonObject().put("success", false));
                        }
                    } else {
                        LOGGER.error("Cannot extract pack from mocked device because it is not in the folder");
                        eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                    }
                }, 1, TimeUnit.SECONDS);
            return CompletableFuture.completedFuture(Optional.of(transferId));
        }
    }

    private JsonObject getPackMetadata(StoryPackMetadata packMetadata) {
        JsonObject json = new JsonObject()
                .put("uuid", packMetadata.getUuid())
                .put("format", packMetadata.getFormat())
                .put("version", packMetadata.getVersion())
                .put("sectorSize", packMetadata.getSectorSize());
        return databaseMetadataService.getPackMetadata(packMetadata.getUuid())
                .map(metadata -> json
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("official", metadata.isOfficial())
                )
                .orElse(json);
    }

    public CompletableFuture<Void> dump(Path outputPath) {
        // Not supported
        LOGGER.warn("Not supported : dump");
        return CompletableFuture.completedFuture(null);
    }

}
