/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.webui.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.Device;

import io.quarkus.arc.profile.IfBuildProfile;
import io.vertx.mutiny.core.eventbus.EventBus;
import studio.core.v1.exception.NoDevicePluggedException;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.utils.io.FileUtils;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.core.v1.model.TransferProgressListener;
import studio.core.v1.model.TransferProgressListener.TransferStatus;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.MetaPackDTO;
import studio.webui.model.DeviceDTOs.TransferDTO;
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

    @ConfigProperty(name = "studio.library")
    Path libraryPath;

    private StoryTellerAsyncDriver rawDriver;
    private StoryTellerAsyncDriver fsDriver;

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
                .orElseThrow(NoDevicePluggedException::new)
                .thenApply(this::enhancePacks);
    }

    @Override
    public TransferDTO addPack(UUID uuid, String packName) {
        return currentDriver().map(d -> //
            d.getPacksList().toCompletableFuture() //
            .thenApply(packs -> upload(packs, d, uuid, packName)).join()) //
        .orElseThrow(NoDevicePluggedException::new);
    }

    @Override
    public CompletionStage<Boolean> deletePack(UUID uuid) {
        return currentDriver().map(d -> d.deletePack(uuid)) //
                .orElseThrow(NoDevicePluggedException::new);
    }

    @Override
    public CompletionStage<Boolean> reorderPacks(List<UUID> uuids) {
        return currentDriver().map(d -> d.reorderPacks(uuids)) //
                .orElseThrow(NoDevicePluggedException::new);
    }

    @Override
    public TransferDTO extractPack(UUID uuid) {
        return currentDriver().map(d -> download(d, uuid)) //
                .orElseThrow(NoDevicePluggedException::new);
    }

    @Override
    public CompletionStage<Void> dump(Path outputPath) {
        return currentDriver().map(d -> d.dump(outputPath)) //
                .orElseThrow(NoDevicePluggedException::new);
    }

    // Send event on eventbus to monitor progress
    private TransferProgressListener onTransferProgress(String transferId) {
        return status -> {
            double p = status.getPercent();
            if(LOGGER.isInfoEnabled()) {
                LOGGER.info("Transferring {} ({} / {})", //
                   FileUtils.readablePercent(p), //
                   FileUtils.readableByteSize(status.getTransferred()), //
                   FileUtils.readableByteSize(status.getTotal()));
            }
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

    private TransferDTO upload(List<MetaPackDTO> packs, StoryTellerAsyncDriver driver, UUID uuid, String packName) {
        // Check that the pack is on device : Look for UUID in packs index
        boolean matched = packs.stream().map(MetaPackDTO::getUuid).anyMatch(uuid::equals);
        if (matched) {
            throw new StoryTellerException("Pack already exists on device");
        }
        Path packFile = libraryPath.resolve(packName);
        String transferId = UUID.randomUUID().toString();
        driver.uploadPack(uuid, packFile, onTransferProgress(transferId)).whenComplete(onTransferEnd(transferId));
        return new TransferDTO(transferId);
    }

    private TransferDTO download(StoryTellerAsyncDriver driver, UUID uuid) {
        String transferId = UUID.randomUUID().toString();
        driver.downloadPack(uuid, libraryPath, onTransferProgress(transferId)).whenComplete(onTransferEnd(transferId));
        return new TransferDTO(transferId);
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
