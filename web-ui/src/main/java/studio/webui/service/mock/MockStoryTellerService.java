/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service.mock;

import static studio.core.v1.utils.io.FileUtils.dataInputStream;
import static studio.core.v1.utils.io.FileUtils.dataOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.service.PackFormat;
import studio.core.v1.service.raw.RawStoryPackReader;
import studio.core.v1.utils.io.FileUtils;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.DeviceInfosDTO.StorageDTO;
import studio.driver.model.MetaPackDTO;
import studio.metadata.DatabaseMetadataService;
import studio.webui.service.IStoryTellerService;

@UnlessBuildProfile("prod")
@Singleton
public class MockStoryTellerService implements IStoryTellerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockStoryTellerService.class);

    private static final int BUFFER_SIZE = 1024 * 1024 * 10;

    @Inject
    EventBus eventBus;

    @Inject
    DatabaseMetadataService databaseMetadataService;

    @ConfigProperty(name = "studio.mock.device")
    Path devicePath;

    public void onStart(@Observes StartupEvent ev) {
        LOGGER.info("Setting up mocked story teller service in {}", devicePath);
        // Create the mocked device folder if needed
        FileUtils.createDirectories("Failed to initialize mocked device", devicePath);
        // plug event
        sendDevicePlugged(eventBus, getDeviceInfo());
    }

    public void onStop(@Observes ShutdownEvent ev) {
        // unplug event
        sendDeviceUnplugged(eventBus);
    }

    @Override
    public CompletionStage<DeviceInfosDTO> deviceInfos() {
        return CompletableFuture.completedStage(getDeviceInfo());
    }

    @Override
    public CompletionStage<List<MetaPackDTO>> packs() {
        return readPackIndex(devicePath).thenApply(p -> p.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @Override
    public CompletionStage<String> addPack(UUID uuid, Path packFile) {
        return copyPack("add pack", packFile, getDevicePack(uuid));
    }

    @Override
    public CompletionStage<String> extractPack(UUID uuid, Path destFile) {
        return copyPack("extract pack", getDevicePack(uuid), destFile);
    }

    @Override
    public CompletionStage<Boolean> deletePack(UUID uuid) {
        try {
            LOGGER.warn("Remove pack {}", uuid);
            return CompletableFuture.completedStage(Files.deleteIfExists(getDevicePack(uuid)));
        } catch (IOException e) {
            LOGGER.error("Failed to remove pack from mocked device", e);
            return CompletableFuture.completedStage(false);
        }
    }

    @Override
    public CompletionStage<Boolean> reorderPacks(List<UUID> uuids) {
        return readPackIndex(devicePath).thenApply(p -> { //
            List<UUID> packs = p.stream().map(StoryPackMetadata::getUuid).collect(Collectors.toList()); //
            LOGGER.info("Device packs {}", packs);
            return packs.containsAll(uuids); //
        });
    }

    @Override
    public CompletionStage<Void> dump(Path outputPath) {
        LOGGER.warn("Not supported : dump");
        return CompletableFuture.completedStage(null);
    }

    private Path getDevicePack(UUID uuid) {
        return devicePath.resolve(uuid + PackFormat.RAW.getExtension());
    }

    private CompletionStage<List<StoryPackMetadata>> readPackIndex(Path deviceFolder) {
        // List binary pack files in mocked device folder
        try (Stream<Path> paths = Files.walk(deviceFolder).filter(Files::isRegularFile)) {
            return CompletableFuture.completedStage( //
                    paths.map(this::readBinaryPackFile) //
                            .filter(Objects::nonNull) //
                            .collect(Collectors.toList()) //
            );
        } catch (IOException e) {
            throw new StoryTellerException("Failed to read packs from mocked device", e);
        }
    }

    private StoryPackMetadata readBinaryPackFile(Path path) {
        LOGGER.debug("Reading pack file: {}", path);
        // Handle only binary file format
        if (PackFormat.fromPath(path) != PackFormat.RAW) {
            LOGGER.error("Mocked device should only contain .pack files");
            return null;
        }
        try {
            LOGGER.debug("Reading binary pack metadata.");
            StoryPackMetadata meta = new RawStoryPackReader().readMetadata(path);
            if (meta != null) {
                meta.setSectorSize((int) Math.ceil(Files.size(path) / 512d));
                return meta;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read binary-format pack {} from mocked device", path, e);
        }
        // Ignore other files
        return null;
    }

    private CompletionStage<String> copyPack(String opName, Path packFile, Path destFile) {
        String transferId = UUID.randomUUID().toString();
        // Perform transfer asynchronously, and send events on eventbus to monitor
        // progress and end of transfer
        Executor after300ms = CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(() -> {
            // Check that source and destination are available
            if (Files.notExists(packFile)) {
                LOGGER.warn("Cannot {} : pack doesn't exist {}", opName, packFile);
                sendDone(eventBus, transferId, false);
                return;
            }
            if (Files.exists(destFile)) {
                LOGGER.warn("{} : destination already exists : {}", opName, destFile);
                sendDone(eventBus, transferId, true);
                return;
            }
            try (InputStream input = dataInputStream(packFile);
                 OutputStream output = dataOutputStream(destFile)) {
                long fileSize = Files.size(packFile);
                final byte[] buffer = new byte[BUFFER_SIZE];
                long count = 0;
                int n = 0;
                while ((n = input.read(buffer)) != -1) {
                    output.write(buffer, 0, n);
                    count += n;
                    // Send events on eventbus to monitor progress
                    double p = count / (double) fileSize;
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Copying pack... {} ({} / {})", new DecimalFormat("#%").format(p),
                                FileUtils.readableByteSize(count), FileUtils.readableByteSize(fileSize));
                    }
                    sendProgress(eventBus, transferId, p);
                }
                LOGGER.info("Pack copied ({})", transferId);
                // Send event on eventbus to signal end of transfer
                sendDone(eventBus, transferId, true);
            } catch (IOException e) {
                LOGGER.error("Failed to {} on mocked device", opName, e);
                // Send event on eventbus to signal transfer failure
                sendDone(eventBus, transferId, false);
            }
        }, after300ms);
        return CompletableFuture.completedStage(transferId);
    }

    private DeviceInfosDTO getDeviceInfo() {
        try {
            FileStore mdFd = Files.getFileStore(devicePath);
            long total = mdFd.getTotalSpace();
            long used = mdFd.getTotalSpace() - mdFd.getUnallocatedSpace();

            DeviceInfosDTO di = new DeviceInfosDTO();
            di.setUuid(new UUID(0, 0));
            di.setSerial("mocked-serial");
            di.setFirmware("mocked-version");
            di.setError(false);
            di.setPlugged(true);
            di.setDriver("raw"); // Simulate raw only
            di.setStorage(new StorageDTO(total, total - used, used));
            return di;
        } catch (IOException e) {
            throw new StoryTellerException("Failed to initialize mocked device", e);
        }
    }

    private MetaPackDTO toDto(StoryPackMetadata pack) {
        MetaPackDTO mp = new MetaPackDTO();
        mp.setUuid(pack.getUuid());
        mp.setFormat(PackFormat.RAW.getLabel());
        mp.setVersion(pack.getVersion());
        mp.setSectorSize(pack.getSectorSize());
        // add meta
        databaseMetadataService.getMetadata(pack.getUuid()).ifPresent(meta -> {//
            mp.setTitle(meta.getTitle());
            mp.setDescription(meta.getDescription());
            mp.setImage(meta.getThumbnail());
            mp.setOfficial(meta.isOfficial());
        });
        LOGGER.debug("toDto : {}", mp);
        return mp;
    }
}
