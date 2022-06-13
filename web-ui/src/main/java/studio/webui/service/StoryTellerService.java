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
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.usb4java.Device;

import io.quarkus.arc.profile.UnlessBuildProfile;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import studio.core.v1.utils.PackFormat;
import studio.core.v1.utils.SecurityUtils;
import studio.core.v1.utils.exception.StoryTellerException;
import studio.driver.StoryTellerAsyncDriver;
import studio.driver.fs.FsStoryTellerAsyncDriver;
import studio.driver.model.StoryPackInfos;
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

@UnlessBuildProfile("dev")
@ApplicationScoped
public class StoryTellerService implements IStoryTellerService {

    private static final Logger LOGGER = LogManager.getLogger(StoryTellerService.class);

    @Inject
    EventBus eventBus;

    @Inject
    DatabaseMetadataService databaseMetadataService;

    private RawStoryTellerAsyncDriver rawDriver;
    private FsStoryTellerAsyncDriver fsDriver;
    private Device rawDevice;
    private Device fsDevice;

    public StoryTellerService() {
        // public StoryTellerService(EventBus eventBus, DatabaseMetadataService
        // databaseMetadataService) {
//        this.eventBus = eventBus;
//        this.databaseMetadataService = databaseMetadataService;

        LOGGER.info("Setting up story teller driver");
        rawDriver = new RawStoryTellerAsyncDriver();
        fsDriver = new FsStoryTellerAsyncDriver();

        // React when a device with firmware 1.x is plugged or unplugged
        rawDriver.registerDeviceListener( //
                device -> {
                    if (device == null) {
                        LOGGER.error("Device 1.x plugged but got null device");
                        // Send 'failure' event on bus
                        sendFailure();
                    } else {
                        LOGGER.info("Device 1.x plugged");
                        StoryTellerService.this.rawDevice = device;
                        CompletableFuture.runAsync(() -> rawDriver.getDeviceInfos().handle((infos, e) -> {
                            if (e != null) {
                                LOGGER.error("Failed to plug device 1.x", e);
                                // Send 'failure' event on bus
                                sendFailure();
                            } else {
                                // Send 'plugged' event on bus
                                sendDevicePlugged(toDto(infos));
                            }
                            return null;
                        }));
                    }
                }, //
                device -> {
                    LOGGER.info("Device 1.x unplugged");
                    StoryTellerService.this.rawDevice = null;
                    sendDeviceUnplugged();
                });

        // React when a device with firmware 2.x is plugged or unplugged
        fsDriver.registerDeviceListener( //
                device -> {
                    if (device == null) {
                        LOGGER.error("Device 2.x plugged but got null device");
                        // Send 'failure' event on bus
                        sendFailure();
                    } else {
                        LOGGER.info("Device 2.x plugged");
                        StoryTellerService.this.fsDevice = device;
                        CompletableFuture.runAsync(() -> fsDriver.getDeviceInfos().handle((infos, e) -> {
                            if (e != null) {
                                LOGGER.error("Failed to plug device 2.x", e);
                                // Send 'failure' event on bus
                                sendFailure();
                            } else {
                                // Send 'plugged' event on bus
                                sendDevicePlugged(toDto(infos));
                            }
                            return null;
                        }));
                    }
                }, //
                device -> {
                    LOGGER.info("Device 2.x unplugged");
                    StoryTellerService.this.fsDevice = null;
                    sendDeviceUnplugged();
                });
    }

    public CompletionStage<DeviceInfosDTO> deviceInfos() {
        if (rawDevice != null) {
            return rawDriver.getDeviceInfos().thenApply(this::toDto);
        }
        if (fsDevice != null) {
            return fsDriver.getDeviceInfos().thenApply(this::toDto);
        }
        DeviceInfosDTO failed = new DeviceInfosDTO();
        failed.setPlugged(false);
        return CompletableFuture.completedStage(failed);
        // return CompletableFuture.completedStage(new JsonObject().put("plugged",
        // false));
    }

    public CompletionStage<List<MetaPackDTO>> packs() {
        if (rawDevice != null) {
            return rawDriver.getPacksList().thenApply(p -> p.stream().map(this::toDto).collect(Collectors.toList()));
        }
        if (fsDevice != null) {
            return fsDriver.getPacksList().thenApply(p -> p.stream().map(this::toDto).collect(Collectors.toList()));
        }
        return CompletableFuture.completedStage(Arrays.asList());
    }

    @Deprecated
    public CompletionStage<JsonArray> packs0() {
        if (rawDevice != null) {
            return rawDriver.getPacksList().thenApply(
                    packs -> new JsonArray(packs.stream().map(this::getRawPackMetadata).collect(Collectors.toList())));
        }
        if (fsDevice != null) {
            return fsDriver.getPacksList().thenApply(
                    packs -> new JsonArray(packs.stream().map(this::getFsPackMetadata).collect(Collectors.toList())));
        }
        return CompletableFuture.completedStage(new JsonArray());
    }

    public CompletionStage<String> addPack(String uuid, Path packFile) {
        if (rawDevice != null) {
            return rawDriver.getPacksList().thenApply(packs -> upload(packs, rawDriver, uuid, packFile));
        }
        if (fsDevice != null) {
            return fsDriver.getPacksList().thenApply(packs -> upload(packs, fsDriver, uuid, packFile));
        }
        // return CompletableFuture.completedStage(Optional.empty());
        return CompletableFuture.completedStage(uuid);
    }

    public CompletionStage<Boolean> deletePack(String uuid) {
        if (rawDevice != null) {
            return rawDriver.deletePack(uuid);
        }
        if (fsDevice != null) {
            return fsDriver.deletePack(uuid);
        }
        return CompletableFuture.completedStage(false);
    }

    public CompletionStage<Boolean> reorderPacks(List<String> uuids) {
        if (rawDevice != null) {
            return rawDriver.reorderPacks(uuids);
        }
        if (fsDevice != null) {
            return fsDriver.reorderPacks(uuids);
        }
        return CompletableFuture.completedStage(false);
    }

    public CompletionStage<String> extractPack(String uuid, Path packFile) {
        if (rawDevice != null) {
            return CompletableFuture.completedStage(download(rawDriver, uuid, packFile));
        }
        if (fsDevice != null) {
            return CompletableFuture.completedStage(download(fsDriver, uuid, packFile));
        }
        // return CompletableFuture.completedStage(Optional.empty());
        return CompletableFuture.completedStage(uuid);
    }

    public CompletionStage<Void> dump(Path outputPath) {
        if (rawDevice != null) {
            return rawDriver.dump(outputPath);
        }
        // unavailable for fsDevice
        return CompletableFuture.completedStage(null);
    }

    private void sendDevicePlugged(DeviceInfosDTO infosDTO) {
        eventBus.publish("storyteller.plugged", JsonObject.mapFrom(infosDTO));
    }

    private void sendDeviceUnplugged() {
        eventBus.send("storyteller.unplugged", null);
    }

    private void sendFailure() {
        eventBus.send("storyteller.failure", null);
    }

    private void sendProgress(String id, double p) {
        eventBus.send("storyteller.transfer." + id + ".progress", new JsonObject().put("progress", p));
    }

    private void sendDone(String id, boolean success) {
        eventBus.send("storyteller.transfer." + id + ".done", new JsonObject().put("success", success));
    }

    // private <T, U, V extends StoryPackInfos> Optional<String> upload(List<V>
    // packs, StoryTellerAsyncDriver<T, U> driver,
    private <T, U, V extends StoryPackInfos> String upload(List<V> packs, StoryTellerAsyncDriver<T, U> driver,
            String uuid, Path packFile) {
        // Check that the pack is not already on the device : Look for UUID in packs
        // index
        boolean matched = packs.stream().anyMatch(p -> p.getUuid().equals(UUID.fromString(uuid)));
        if (matched) {
            LOGGER.warn("Pack already exists on device");
            // return Optional.empty();
            sendDone(uuid, true);
            return uuid;
        }
        String transferId = UUID.randomUUID().toString();
        try {
            driver.uploadPack(uuid, packFile, status -> {
                // Send event on eventbus to monitor progress
                double p = status.getPercent();
                LOGGER.debug("Pack add progress... {}% ({} / {})", p, status.getTransferred(), status.getTotal());
                sendProgress(transferId, p);
            }).whenComplete((status, t) -> {
                // Handle failure
                if (t != null) {
                    throw new StoryTellerException(t);
                }
                // Handle success
                sendDone(transferId, true);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to add pack to device", e);
            // Send event on eventbus to signal transfer failure
            sendDone(transferId, false);
        }
        // return Optional.of(transferId);
        return transferId;
    }

    // private <T, U> Optional<String> download(StoryTellerAsyncDriver<T, U> driver,
    // String uuid, Path destFile) {
    private <T, U> String download(StoryTellerAsyncDriver<T, U> driver, String uuid, Path destFile) {
        // Check that the destination is available
        if (Files.exists(destFile.resolve(uuid))) {
            LOGGER.warn("Cannot extract pack from device because the destination file already exists");
            // return Optional.empty();
            sendDone(uuid, true);
            return uuid;
        }
        String transferId = UUID.randomUUID().toString();
        try {
            driver.downloadPack(uuid, destFile, status -> {
                // Send event on eventbus to monitor progress
                double p = status.getPercent();
                LOGGER.debug("Pack extraction progress... {}% ({} / {})", p, status.getTransferred(),
                        status.getTotal());
                sendProgress(transferId, p);
            }).whenComplete((status, t) -> {
                // Handle failure
                if (t != null) {
                    throw new StoryTellerException(t);
                }
                // Handle success
                sendDone(transferId, true);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to extract pack from device", e);
            // Send event on eventbus to signal transfer failure
            sendDone(transferId, false);
        }
        // return Optional.of(transferId);
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
        databaseMetadataService.getPackMetadata(pack.getUuid().toString()).ifPresent(meta -> {//
            mp.setTitle(meta.getTitle());
            mp.setDescription(meta.getDescription());
            mp.setImage(meta.getThumbnail());
            mp.setOfficial(meta.isOfficial());
        });
        LOGGER.info("toDto : {}", mp);
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
        databaseMetadataService.getPackMetadata(pack.getUuid().toString()).ifPresent(meta -> {//
            mp.setTitle(meta.getTitle());
            mp.setDescription(meta.getDescription());
            mp.setImage(meta.getThumbnail());
            mp.setOfficial(meta.isOfficial());
        });
        LOGGER.debug("toDto : {}", mp);
        return mp;
    }

    @Deprecated
    private JsonObject getRawPackMetadata(RawStoryPackInfos pack) {
        JsonObject json = new JsonObject() //
                .put("uuid", pack.getUuid().toString()) //
                .put("format", PackFormat.RAW.getLabel()) //
                .put("version", pack.getVersion()) //
                .put("sectorSize", pack.getSizeInSectors());

        return databaseMetadataService.getPackMetadata(pack.getUuid().toString()).map(meta -> json //
                .put("title", meta.getTitle()) //
                .put("description", meta.getDescription()) //
                .put("image", meta.getThumbnail()) //
                .put("official", meta.isOfficial())) //
                .orElse(json);
    }

    @Deprecated
    private JsonObject getFsPackMetadata(FsStoryPackInfos pack) {
        JsonObject json = new JsonObject() //
                .put("uuid", pack.getUuid().toString()) //
                .put("format", PackFormat.FS.getLabel()) //
                .put("version", pack.getVersion()) //
                .put("folderName", pack.getFolderName()) //
                .put("sizeInBytes", pack.getSizeInBytes()) //
                .put("nightModeAvailable", pack.isNightModeAvailable());

        return databaseMetadataService.getPackMetadata(pack.getUuid().toString()).map(meta -> json //
                .put("title", meta.getTitle()) //
                .put("description", meta.getDescription()) //
                .put("image", meta.getThumbnail()) //
                .put("official", meta.isOfficial())) //
                .orElse(json);
    }

    private DeviceInfosDTO toDto(RawDeviceInfos infos) {
        long total = (long) infos.getSdCardSizeInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE;
        long used = (long) infos.getUsedSpaceInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE;
        String fw = infos.getFirmwareMajor() == -1 ? null : infos.getFirmwareMajor() + "." + infos.getFirmwareMinor();

        DeviceInfosDTO di = new DeviceInfosDTO();
        di.setUuid(infos.getUuid().toString());
        di.setSerial(infos.getSerialNumber());
        di.setFirmware(fw); //
        di.setError(infos.isInError()); //
        di.setPlugged(true); //
        di.setDriver(PackFormat.RAW.getLabel());
        di.setStorage(new StorageDTO(total, total - used, used));
        return di;
//        return new JsonObject() //
//                .put("uuid", infos.getUuid().toString()) //
//                .put("serial", infos.getSerialNumber()) //
//                .put("firmware", fw) //
//                .put("storage", new JsonObject() //
//                        .put("size", total)//
//                        .put("free", total - used)//
//                        .put("taken", used))
//                .put("error", infos.isInError()) //
//                .put("plugged", true) //
//                .put("driver", "raw");
    }

    private DeviceInfosDTO toDto(FsDeviceInfos infos) {
        long total = infos.getSdCardSizeInBytes();
        long used = infos.getUsedSpaceInBytes();

        DeviceInfosDTO di = new DeviceInfosDTO();
        di.setUuid(SecurityUtils.encodeHex(infos.getDeviceId()));
        di.setSerial(infos.getSerialNumber());
        di.setFirmware(infos.getFirmwareMajor() + "." + infos.getFirmwareMinor()); //
        di.setError(false); //
        di.setPlugged(true); //
        di.setDriver(PackFormat.FS.getLabel());
        di.setStorage(new StorageDTO(total, total - used, used));
        return di;
//        return new JsonObject() //
//                .put("uuid", SecurityUtils.encodeHex(infos.getDeviceId())) //
//                .put("serial", infos.getSerialNumber()) //
//                .put("firmware", infos.getFirmwareMajor() + "." + infos.getFirmwareMinor()) //
//                .put("storage", new JsonObject() //
//                        .put("size", infos.getSdCardSizeInBytes()) //
//                        .put("free", infos.getSdCardSizeInBytes() - infos.getUsedSpaceInBytes()) //
//                        .put("taken", infos.getUsedSpaceInBytes())) //
//                .put("error", false) //
//                .put("plugged", true) //
//                .put("driver", "fs");
    }

}
