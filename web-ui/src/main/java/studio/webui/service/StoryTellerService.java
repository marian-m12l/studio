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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.usb4java.Device;

import io.quarkus.arc.profile.IfBuildProfile;
import io.vertx.mutiny.core.eventbus.EventBus;
import studio.core.v1.utils.PackFormat;
import studio.core.v1.utils.SecurityUtils;
import studio.driver.StoryTellerAsyncDriver;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.fs.FsStoryTellerAsyncDriver;
import studio.driver.model.StoryPackInfos;
import studio.driver.model.TransferStatus;
import studio.driver.model.fs.FsDeviceInfos;
import studio.driver.model.fs.FsStoryPackInfos;
import studio.driver.model.raw.RawDeviceInfos;
import studio.driver.model.raw.RawStoryPackInfos;
import studio.driver.raw.LibUsbMassStorageHelper;
import studio.driver.raw.RawStoryTellerAsyncDriver;
import studio.metadata.DatabaseMetadataService;
import studio.webui.model.DeviceDTOs.DeviceInfosDTO;
import studio.webui.model.DeviceDTOs.DeviceInfosDTO.StorageDTO;
import studio.webui.model.LibraryDTOs.MetaPackDTO;

@IfBuildProfile("prod")
@Singleton
public class StoryTellerService implements IStoryTellerService, DevicePluggedListener, DeviceUnpluggedListener {

    private static final Logger LOGGER = LogManager.getLogger(StoryTellerService.class);

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
        } else {
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
    }

    @Override
    public void onDeviceUnplugged(Device device) {
        LOGGER.info("Device unplugged");
        sendDeviceUnplugged(eventBus);
    }

    public CompletionStage<DeviceInfosDTO> deviceInfos() {
        if (rawDriver.hasDevice()) {
            return rawDriver.getDeviceInfos().thenApply(this::toDto);
        }
        if (fsDriver.hasDevice()) {
            return fsDriver.getDeviceInfos().thenApply(this::toDto);
        }
        DeviceInfosDTO failed = new DeviceInfosDTO();
        failed.setPlugged(false);
        return CompletableFuture.completedStage(failed);
    }

    public CompletionStage<List<MetaPackDTO>> packs() {
        if (rawDriver.hasDevice()) {
            return rawDriver.getPacksList().thenApply(p -> p.stream().map(this::toDto).collect(Collectors.toList()));
        }
        if (fsDriver.hasDevice()) {
            return fsDriver.getPacksList().thenApply(p -> p.stream().map(this::toDto).collect(Collectors.toList()));
        }
        return CompletableFuture.completedStage(Arrays.asList());
    }

    public CompletionStage<String> addPack(String uuid, Path packFile) {
        if (rawDriver.hasDevice()) {
            return rawDriver.getPacksList().thenApply(packs -> upload(packs, rawDriver, uuid, packFile));
        }
        if (fsDriver.hasDevice()) {
            return fsDriver.getPacksList().thenApply(packs -> upload(packs, fsDriver, uuid, packFile));
        }
        return CompletableFuture.completedStage(uuid);
    }

    public CompletionStage<Boolean> deletePack(String uuid) {
        if (rawDriver.hasDevice()) {
            return rawDriver.deletePack(uuid);
        }
        if (fsDriver.hasDevice()) {
            return fsDriver.deletePack(uuid);
        }
        return CompletableFuture.completedStage(false);
    }

    public CompletionStage<Boolean> reorderPacks(List<String> uuids) {
        if (rawDriver.hasDevice()) {
            return rawDriver.reorderPacks(uuids);
        }
        if (fsDriver.hasDevice()) {
            return fsDriver.reorderPacks(uuids);
        }
        return CompletableFuture.completedStage(false);
    }

    public CompletionStage<String> extractPack(String uuid, Path packFile) {
        if (rawDriver.hasDevice()) {
            return CompletableFuture.completedStage(download(rawDriver, uuid, packFile));
        }
        if (fsDriver.hasDevice()) {
            return CompletableFuture.completedStage(download(fsDriver, uuid, packFile));
        }
        return CompletableFuture.completedStage(uuid);
    }

    public CompletionStage<Void> dump(Path outputPath) {
        if (rawDriver.hasDevice()) {
            return rawDriver.dump(outputPath);
        }
        // unavailable for fsDevice
        return CompletableFuture.completedStage(null);
    }

    // Send event on eventbus to monitor progress
    private TransferProgressListener onTransferProgress(String transferId) {
        return status -> {
            double p = status.getPercent();
            LOGGER.debug("Transfer progress... {}% ({} / {})", p, status.getTransferred(), status.getTotal());
            sendProgress(eventBus, transferId, p);
        };
    }

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

    private <T, U, V extends StoryPackInfos> String upload(List<V> packs, StoryTellerAsyncDriver<T, U> driver,
            String uuid, Path packFile) {
        // Check that the pack on device : Look for UUID in packs index
        boolean matched = packs.stream().anyMatch(p -> p.getUuid().equals(UUID.fromString(uuid)));
        if (matched) {
            LOGGER.warn("Pack already exists on device");
            sendDone(eventBus, uuid, true);
            return uuid;
        }
        String transferId = UUID.randomUUID().toString();
        driver.uploadPack(uuid, packFile, onTransferProgress(transferId)).whenComplete(onTransferEnd(transferId));
        return transferId;
    }

    private <T, U> String download(StoryTellerAsyncDriver<T, U> driver, String uuid, Path destFile) {
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

    private MetaPackDTO toDto(RawStoryPackInfos pack) {
        MetaPackDTO mp = new MetaPackDTO();
        mp.setUuid(pack.getUuid().toString());
        mp.setFormat(PackFormat.RAW.getLabel());
        mp.setVersion(pack.getVersion());
        // raw data
        mp.setSectorSize(pack.getSizeInSectors());
        // add meta
        databaseMetadataService.getPackMetadata(pack.getUuid().toString()).ifPresent(meta -> { //
            mp.setTitle(meta.getTitle());
            mp.setDescription(meta.getDescription());
            mp.setImage(meta.getThumbnail());
            mp.setOfficial(meta.isOfficial());
        });
        LOGGER.trace("toDto : {}", mp);
        return mp;
    }

    private MetaPackDTO toDto(FsStoryPackInfos pack) {
        MetaPackDTO mp = new MetaPackDTO();
        mp.setUuid(pack.getUuid().toString());
        mp.setFormat(PackFormat.FS.getLabel());
        mp.setVersion(pack.getVersion());
        // fs data
        mp.setFolderName(pack.getFolderName());
        mp.setSizeInBytes(pack.getSizeInBytes());
        mp.setNightModeAvailable(pack.isNightModeAvailable());
        // add meta
        databaseMetadataService.getPackMetadata(pack.getUuid().toString()).ifPresent(meta -> { //
            mp.setTitle(meta.getTitle());
            mp.setDescription(meta.getDescription());
            mp.setImage(meta.getThumbnail());
            mp.setOfficial(meta.isOfficial());
        });
        LOGGER.trace("toDto : {}", mp);
        return mp;
    }

    private DeviceInfosDTO toDto(RawDeviceInfos infos) {
        long total = (long) infos.getSdCardSizeInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE;
        long used = (long) infos.getUsedSpaceInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE;
        String fw = infos.getFirmwareMajor() == -1 ? null : infos.getFirmwareMajor() + "." + infos.getFirmwareMinor();

        DeviceInfosDTO di = new DeviceInfosDTO();
        di.setUuid(infos.getUuid().toString());
        di.setSerial(infos.getSerialNumber());
        di.setFirmware(fw);
        di.setError(infos.isInError());
        di.setPlugged(true);
        di.setDriver(PackFormat.RAW.getLabel());
        di.setStorage(new StorageDTO(total, total - used, used));
        return di;
    }

    private DeviceInfosDTO toDto(FsDeviceInfos infos) {
        long total = infos.getSdCardSizeInBytes();
        long used = infos.getUsedSpaceInBytes();

        DeviceInfosDTO di = new DeviceInfosDTO();
        di.setUuid(SecurityUtils.encodeHex(infos.getDeviceId()));
        di.setSerial(infos.getSerialNumber());
        di.setFirmware(infos.getFirmwareMajor() + "." + infos.getFirmwareMinor());
        di.setError(false);
        di.setPlugged(true);
        di.setDriver(PackFormat.FS.getLabel());
        di.setStorage(new StorageDTO(total, total - used, used));
        return di;
    }

}
