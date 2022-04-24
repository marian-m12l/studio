/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service.mock;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import studio.config.StudioConfig;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.binary.BinaryStoryPackReader;
import studio.core.v1.utils.exception.StoryTellerException;
import studio.metadata.DatabaseMetadataService;
import studio.webui.service.IStoryTellerService;

public class MockStoryTellerService implements IStoryTellerService {

    private static final Logger LOGGER = LogManager.getLogger(MockStoryTellerService.class);

    private static final int BUFFER_SIZE = 1024 * 1024 * 10;

    private final EventBus eventBus;

    private final DatabaseMetadataService databaseMetadataService;

    public MockStoryTellerService(EventBus eventBus, DatabaseMetadataService databaseMetadataService) {
        this.eventBus = eventBus;
        this.databaseMetadataService = databaseMetadataService;

        LOGGER.info("Setting up mocked story teller service");

        // Create the mocked device folder if needed
        try {
            Path libraryFolder = devicePath();
            Files.createDirectories(libraryFolder);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize mocked device", e);
            throw new IllegalStateException("Failed to initialize mocked device");
        }
    }

    private void sendProgress(String id, double p) {
        eventBus.send("storyteller.transfer." + id + ".progress", new JsonObject().put("progress", p));
    }

    private void sendDone(String id, boolean success) {
        eventBus.send("storyteller.transfer." + id + ".done", new JsonObject().put("success", success));
    }

    private static Path devicePath() {
        return Path.of(StudioConfig.STUDIO_MOCK_DEVICE.getValue());
    }

    public CompletionStage<JsonObject> deviceInfos() {
        try(Stream<Path> paths = Files.list(devicePath())) {
            long files = paths.count();
            return CompletableFuture.completedFuture( //
                    new JsonObject() //
                            .put("uuid", "mocked-device") //
                            .put("serial", "mocked-serial") //
                            .put("firmware", "mocked-version") //
                            .put("storage", new JsonObject() //
                                    .put("size", files) //
                                    .put("free", 0) //
                                    .put("taken", files) //
                            ) //
                            .put("error", false) //
                            .put("driver", "raw") // Simulate raw only
            );
        } catch (IOException e) {
            LOGGER.error("Failed to initialize mocked device", e);
            throw new StoryTellerException(e);
        }
    }

    public CompletionStage<JsonArray> packs() {
        // Check that mocked device folder exists
        Path deviceFolder = devicePath();
        if (!Files.isDirectory(deviceFolder)) {
            return CompletableFuture.completedFuture(new JsonArray());
        } else {
            return readPackIndex(deviceFolder).thenApply(packs -> new JsonArray( //
                    packs.stream().map(this::getPackMetadata).collect(Collectors.toList()) //
            ));
        }
    }

    private CompletionStage<List<StoryPackMetadata>> readPackIndex(Path deviceFolder) {
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
            throw new StoryTellerException(e);
        }
    }

    private Optional<StoryPackMetadata> readBinaryPackFile(Path path) {
        LOGGER.debug("Reading pack file: {}", path);
        // Handle only binary file format
        if (path.toString().endsWith(".pack")) {
            try {
                LOGGER.debug("Reading binary pack metadata.");
                StoryPackMetadata meta = new BinaryStoryPackReader().readMetadata(path);
                if (meta != null) {
                    meta.setSectorSize((int)Math.ceil(Files.size(path) / 512d));
                    return Optional.of(meta);
                }
            } catch (IOException e) {
                LOGGER.atError().withThrowable(e).log("Failed to read binary-format pack {} from mocked device", path);
            }
        } else if (path.toString().endsWith(".zip")) {
            LOGGER.error("Mocked device should not contain archive-format packs");
        }
        // Ignore other files
        return Optional.empty();
    }

    public CompletionStage<Optional<String>> addPack(String uuid, Path packFile) {
        // Check that mocked device folder exists
        Path deviceFolder = devicePath();
        if (!Files.isDirectory(deviceFolder)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Path destFile = deviceFolder.resolve(uuid + ".pack");
        return copyPack("add pack", packFile, destFile);
    }

    public CompletionStage<Optional<String>> extractPack(String uuid, Path destFile) {
        // Check that mocked device folder exists
        Path deviceFolder = devicePath();
        if (!Files.isDirectory(deviceFolder)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Path packFile = deviceFolder.resolve(uuid + ".pack");
        return copyPack("extract pack", packFile, destFile);
    }

    private CompletionStage<Optional<String>> copyPack(String opName, Path packFile, Path destFile) {
        String transferId = UUID.randomUUID().toString();
        // Perform transfer asynchronously, and send events on eventbus to monitor
        // progress and end of transfer
        Executor after2s = CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS);
        CompletableFuture.runAsync(() -> {
            // Check that source and destination are available
            if (Files.notExists(packFile) || Files.exists(destFile)) {
                LOGGER.error("Cannot {} : pack doesn't exist or destination does", opName);
                sendDone(transferId, false);
                return;
            }
            try (InputStream input = new BufferedInputStream(Files.newInputStream(packFile));
                    OutputStream output = new BufferedOutputStream(Files.newOutputStream(destFile))) {
                long fileSize = Files.size(packFile);
                final byte[] buffer = new byte[BUFFER_SIZE];
                long count = 0;
                int n = 0;
                while ((n = input.read(buffer)) != -1) {
                    output.write(buffer, 0, n);
                    count += n;
                    // Send events on eventbus to monitor progress
                    double p = count / (double) fileSize;
                    LOGGER.debug("Pack copy progress... {} / {} ({})", count, fileSize, p);
                    sendProgress(transferId, p);
                }
                // Send event on eventbus to signal end of transfer
                sendDone(transferId, true);
            } catch (IOException e) {
                LOGGER.error("Failed to {} on mocked device", opName, e);
                // Send event on eventbus to signal transfer failure
                sendDone(transferId, false);
            }
        }, after2s);
        return CompletableFuture.completedFuture(Optional.of(transferId));
    }

    public CompletionStage<Boolean> deletePack(String uuid) {
        // Check that mocked device folder exists
        Path deviceFolder = devicePath();
        if (!Files.isDirectory(deviceFolder)) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            Path packFile = deviceFolder.resolve(uuid + ".pack");
            if (Files.deleteIfExists(packFile)) {
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

    public CompletionStage<Boolean> reorderPacks(List<String> uuids) {
        // Not supported
        LOGGER.warn("Not supported : reorderPacks");
        return CompletableFuture.completedFuture(false);
    }

    private JsonObject getPackMetadata(StoryPackMetadata packMetadata) {
        JsonObject json = new JsonObject()
                .put("uuid", packMetadata.getUuid())
                .put("format", packMetadata.getFormat().getLabel())
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

    public CompletionStage<Void> dump(Path outputPath) {
        // Not supported
        LOGGER.warn("Not supported : dump");
        return CompletableFuture.completedFuture(null);
    }

}
