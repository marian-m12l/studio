/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.webui.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.Device;

import io.quarkus.arc.profile.IfBuildProfile;
import io.vertx.mutiny.core.eventbus.EventBus;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.MetaPackDTO;
import studio.driver.model.TransferStatus;
import studio.driver.service.StoryTellerAsyncDriver;
import studio.driver.service.fs.FsStoryTellerAsyncDriver;
import studio.driver.service.raw.RawStoryTellerAsyncDriver;
import studio.metadata.DatabaseMetadataService;

@IfBuildProfile("prod")
@Singleton
public class StoryTellerService implements IStoryTellerService, DevicePluggedListener, DeviceUnpluggedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryTellerService.class);

    @Inject
    DatabaseMetadataService databaseMetadataService;

    @Inject
    EventBus eventBus;

    private RawStoryTellerAsyncDriver rawDriver;
    private FsStoryTellerAsyncDriver fsDriver;

    public StoryTellerService() {
        LOGGER.info("Setting up story teller driver");
        // React when a device with firmware 1.x is plugged or unplugged
        rawDriver = new RawStoryTellerAsyncDriver();
        rawDriver.registerDeviceListener(this, this);
        // React when a device with firmware 2.x is plugged or unplugged
        fsDriver = new FsStoryTellerAsyncDriver();
        fsDriver.registerDeviceListener(this, this);
    }

    @Override
    public void onDevicePlugged(Device device) {
        if (device == null) {
            LOGGER.error("Device plugged but is null");
            sendFailure(eventBus);
            return;
        }
        LOGGER.info("Device plugged");
        deviceInfos().whenComplete((infos, e) -> {
            if (e != null) {
                LOGGER.error("Failed to plug device", e);
                sendFailure(eventBus);
            } else {
                sendDevicePlugged(eventBus, infos);
            }
        });
    }

    @Override
    public void onDeviceUnplugged(Device device) {
        LOGGER.info("Device unplugged");
        sendDeviceUnplugged(eventBus);
    }

    /**
     * Select driver from connected device.
     *
     * @return Optional StoryTellerAsyncDriver
     */
    private Optional<StoryTellerAsyncDriver> currentDriver() {
        if (rawDriver.hasDevice()) {
            return Optional.of(rawDriver);
        }
        if (fsDriver.hasDevice()) {
            return Optional.of(fsDriver);
        }
        return Optional.empty();
    }

    @Override
    public CompletionStage<DeviceInfosDTO> deviceInfos() {
        return currentDriver().map(StoryTellerAsyncDriver::getDeviceInfos) //
                .orElseGet(() -> CompletableFuture.completedStage(new DeviceInfosDTO()));
    }

    @Override
    public CompletionStage<List<MetaPackDTO>> packs() {
        return currentDriver().map(StoryTellerAsyncDriver::getPacksList) //
                .orElseGet(() -> CompletableFuture.completedStage(Arrays.asList())) //
                .thenApply(this::enhancePacks);
    }

    @Override
    public CompletionStage<String> addPack(String uuid, Path packFile) {
        return currentDriver().map(d -> d.getPacksList().thenApply(packs -> upload(packs, d, uuid, packFile))) //
                .orElseGet(() -> CompletableFuture.completedStage(uuid));
    }

    @Override
    public CompletionStage<Boolean> deletePack(String uuid) {
        return currentDriver().map(d -> d.deletePack(uuid)) //
                .orElseGet(() -> CompletableFuture.completedStage(false));
    }

    @Override
    public CompletionStage<Boolean> reorderPacks(List<String> uuids) {
        return currentDriver().map(d -> d.reorderPacks(uuids)) //
                .orElseGet(() -> CompletableFuture.completedStage(false));
    }

    @Override
    public CompletionStage<String> extractPack(String uuid, Path packFile) {
        return currentDriver().map(d -> CompletableFuture.completedStage(download(d, uuid, packFile))) //
                .orElseGet(() -> CompletableFuture.completedStage(uuid));
    }

    @Override
    public CompletionStage<Void> dump(Path outputPath) {
        return currentDriver().map(d -> d.dump(outputPath)) //
                .orElseGet(() -> CompletableFuture.completedStage(null));
    }

    // Send event on eventbus to monitor progress
    private TransferProgressListener onTransferProgress(String transferId) {
        return status -> {
            double p = status.getPercent();
            LOGGER.debug("Transfer progress... {}% ({} / {})", p, status.getTransferred(), status.getTotal());
            sendProgress(eventBus, transferId, p);
        };
    }

    // Send event on eventbus when done
    private BiConsumer<TransferStatus, Throwable> onTransferEnd(String transferId) {
        return (status, t) -> {
            boolean state = true;
            if (t != null) {
                LOGGER.error("Failed to transfer pack", t);
                state = false;
            }
            sendDone(eventBus, transferId, state);
        };
    }

    private String upload(List<MetaPackDTO> packs, StoryTellerAsyncDriver driver, String uuid, Path packFile) {
        // Check that the pack on device : Look for UUID in packs index
        boolean matched = packs.stream().anyMatch(p -> p.getUuid().equals(uuid));
        if (matched) {
            LOGGER.warn("Pack already exists on device");
            sendDone(eventBus, uuid, true);
            return uuid;
        }
        String transferId = UUID.randomUUID().toString();
        driver.uploadPack(uuid, packFile, onTransferProgress(transferId)).whenComplete(onTransferEnd(transferId));
        return transferId;
    }

    private String download(StoryTellerAsyncDriver driver, String uuid, Path destFile) {
        // Check that the destination is available
        if (Files.exists(destFile.resolve(uuid))) {
            LOGGER.warn("Cannot extract pack from device because the destination file already exists");
            sendDone(eventBus, uuid, true);
            return uuid;
        }
        String transferId = UUID.randomUUID().toString();
        driver.downloadPack(uuid, destFile, onTransferProgress(transferId)).whenComplete(onTransferEnd(transferId));
        return transferId;
    }

    /**
     * Enhance packs with metadata.
     *
     * @param metaPacks List<MetaPackDTO>
     * @return metaPacks enhanced
     */
    private List<MetaPackDTO> enhancePacks(List<MetaPackDTO> metaPacks) {
        for (MetaPackDTO mp : metaPacks) {
            databaseMetadataService.getMetadata(mp.getUuid()).ifPresent(meta -> { //
                mp.setTitle(meta.getTitle());
                mp.setDescription(meta.getDescription());
                mp.setImage(meta.getThumbnail());
                mp.setOfficial(meta.isOfficial());
            });
        }
        return metaPacks;
    }
}
