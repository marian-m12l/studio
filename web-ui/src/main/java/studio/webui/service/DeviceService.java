/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.webui.service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.Device;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import lombok.Getter;
import studio.core.v1.exception.NoDevicePluggedException;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.utils.io.FileUtils;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.core.v1.model.TransferListener.TransferProgressListener;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.MetaPackDTO;
import studio.webui.model.DeviceDTOs.TransferDTO;
import studio.webui.model.DeviceDTOs.UuidDTO;
import studio.driver.service.StoryTellerAsyncDriver;
import studio.driver.service.fs.FsStoryTellerAsyncDriver;
import studio.driver.service.raw.RawStoryTellerAsyncDriver;
import studio.metadata.DatabaseMetadataService;

@Getter
@ApplicationScoped
public class DeviceService implements DevicePluggedListener, DeviceUnpluggedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceService.class);

    @Inject
    DatabaseMetadataService databaseMetadataService;

    @Inject
    EventBus eventBus;

    @ConfigProperty(name = "studio.library")
    Path libraryPath;

    private RawStoryTellerAsyncDriver rawDriver;
    private FsStoryTellerAsyncDriver fsDriver;

    public DeviceService() {
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
            sendDeviceFailure();
            return;
        }
        LOGGER.info("Device plugged");
        deviceInfos().whenComplete((infos, e) -> {
            if (e != null) {
                LOGGER.error("Failed to plug device", e);
                sendDeviceFailure();
            } else {
                sendDevicePlugged(infos);
            }
        });
    }

    @Override
    public void onDeviceUnplugged(Device device) {
        LOGGER.info("Device unplugged");
        sendDeviceUnplugged();
    }

    /** Select driver from connected device. */
    private StoryTellerAsyncDriver currentDriver() {
        if (fsDriver.hasDevice()) {
            return fsDriver;
        }
        if (rawDriver.hasDevice()) {
            return rawDriver;
        }
        throw new NoDevicePluggedException();
    }

    public CompletionStage<DeviceInfosDTO> deviceInfos() {
        try {
            return currentDriver().getDeviceInfos();
        } catch (NoDevicePluggedException e) {
            return CompletableFuture.completedStage(new DeviceInfosDTO());
        }
    }

    public CompletionStage<List<MetaPackDTO>> packs() {
        return currentDriver().getPacksList().thenApply(this::enhancePacks);
    }

    public TransferDTO addPack(UuidDTO uuidDto) {
        var uuid = uuidDto.getUuid();
        var packFile = libraryPath.resolve(uuidDto.getPath());
        CompletableFuture.supplyAsync(this::currentDriver, Infrastructure.getDefaultWorkerPool()) //
                .thenCompose(d -> d.getPacksList()) //
                .thenApply(packs -> { //
                    boolean matched = packs.stream().map(MetaPackDTO::getUuid).anyMatch(uuid::equals);
                    if (matched) {
                        throw new StoryTellerException("Pack already exists on device: " + uuid);
                    }
                    return currentDriver();
                }) //
                .thenCompose(d -> d.uploadPack(uuid, packFile, transferProgress)) //
                .whenComplete(transferEnd);
        return new TransferDTO(uuid);
    }

    public CompletionStage<Boolean> deletePack(UUID uuid) {
        return currentDriver().deletePack(uuid);
    }

    public CompletionStage<Boolean> reorderPacks(List<UUID> uuids) {
        return currentDriver().reorderPacks(uuids);
    }

    public TransferDTO extractPack(UuidDTO uuidDto) {
        CompletableFuture.supplyAsync(this::currentDriver, Infrastructure.getDefaultWorkerPool()) //
                .thenCompose(d -> d.downloadPack(uuidDto.getUuid(), libraryPath, transferProgress)) //
                .whenComplete(transferEnd); //
        return new TransferDTO(uuidDto.getUuid());
    }

    public CompletionStage<Void> dump(Path outputPath) {
        return currentDriver().dump(outputPath);
    }

    // Send event on eventbus to monitor progress
    private TransferProgressListener transferProgress = status -> {
        double p = status.getPercent();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Transferring {} ({} / {})", //
                    FileUtils.readablePercent(p), //
                    FileUtils.readableByteSize(status.getTransferred()), //
                    FileUtils.readableByteSize(status.getTotal()));
        }
        sendProgress(status.getUuid(), p);
    };

    // Send event on eventbus when done
    private BiConsumer<UUID, Throwable> transferEnd = (uuid, t) -> {
        boolean state = true;
        if (t != null) {
            LOGGER.error("Failed to transfer pack", t);
            state = false;
        }
        sendDone(uuid, state);
    };

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

    void sendDevicePlugged(DeviceInfosDTO infosDTO) {
        eventBus.publish("storyteller.plugged", JsonObject.mapFrom(infosDTO));
    }

    void sendDeviceUnplugged() {
        eventBus.publish("storyteller.unplugged", null);
    }

    void sendDeviceFailure() {
        eventBus.publish("storyteller.failure", null);
    }

    void sendProgress(UUID uuid, double p) {
        eventBus.publish("storyteller.transfer." + uuid + ".progress", new JsonObject().put("progress", p));
    }

    void sendDone(UUID uuid, boolean success) {
        eventBus.publish("storyteller.transfer." + uuid + ".done", new JsonObject().put("success", success));
    }
}
