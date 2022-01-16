/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.usb4java.Device;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.core.v1.Constants;
import studio.core.v1.utils.SecurityUtils;
import studio.driver.event.DeviceHotplugEventListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.fs.FileUtils;
import studio.driver.fs.FsStoryTellerAsyncDriver;
import studio.driver.model.TransferStatus;
import studio.driver.model.fs.FsStoryPackInfos;
import studio.driver.model.raw.RawStoryPackInfos;
import studio.driver.raw.LibUsbMassStorageHelper;
import studio.driver.raw.RawStoryTellerAsyncDriver;
import studio.metadata.DatabaseMetadataService;

public class StoryTellerService implements IStoryTellerService {

    private final Logger LOGGER = LoggerFactory.getLogger(StoryTellerService.class);

    private final EventBus eventBus;

    private final DatabaseMetadataService databaseMetadataService;

    private RawStoryTellerAsyncDriver driver;
    private FsStoryTellerAsyncDriver fsDriver;
    private Device device;
    private Device fsDevice;


    public StoryTellerService(EventBus eventBus, DatabaseMetadataService databaseMetadataService) {
        this.eventBus = eventBus;
        this.databaseMetadataService = databaseMetadataService;

        LOGGER.info("Setting up story teller driver");
        driver = new RawStoryTellerAsyncDriver();
        fsDriver = new FsStoryTellerAsyncDriver();

        // React when a device with firmware 1.x is plugged or unplugged
        driver.registerDeviceListener(new DeviceHotplugEventListener() {
            @Override
            public void onDevicePlugged(Device device) {
                if (device == null) {
                    LOGGER.error("Device 1.x plugged but got null device");
                    // Send 'failure' event on bus
                    eventBus.send("storyteller.failure", null);
                } else {
                    LOGGER.info("Device 1.x plugged");
                    StoryTellerService.this.device = device;
                    CompletableFuture.runAsync(() -> driver.getDeviceInfos()
                            .handle((infos, e) -> {
                                if (e != null) {
                                    LOGGER.error("Failed to plug device 1.x", e);
                                    // Send 'failure' event on bus
                                    eventBus.send("storyteller.failure", null);
                                } else {
                                    JsonObject eventData = new JsonObject()
                                            .put("uuid", infos.getUuid().toString())
                                            .put("serial", infos.getSerialNumber())
                                            .put("firmware", infos.getFirmwareMajor() == -1 ? null : infos.getFirmwareMajor() + "." + infos.getFirmwareMinor())
                                            .put("storage", new JsonObject()
                                                    .put("size", infos.getSdCardSizeInSectors() * (long) LibUsbMassStorageHelper.SECTOR_SIZE)
                                                    .put("free", (infos.getSdCardSizeInSectors() - infos.getUsedSpaceInSectors()) * (long) LibUsbMassStorageHelper.SECTOR_SIZE)
                                                    .put("taken", infos.getUsedSpaceInSectors() * (long) LibUsbMassStorageHelper.SECTOR_SIZE)
                                            )
                                            .put("error", infos.isInError())
                                            .put("driver", "raw");
                                    // Send 'plugged' event on bus
                                    eventBus.send("storyteller.plugged", eventData);
                                }
                                return null;
                            })
                    );
                }
            }

            @Override
            public void onDeviceUnplugged(Device device) {
                LOGGER.info("Device 1.x unplugged");
                StoryTellerService.this.device = null;
                // Send 'unplugged' event on bus
                eventBus.send("storyteller.unplugged", null);
            }
        });

        // React when a device with firmware 2.x is plugged or unplugged
        fsDriver.registerDeviceListener(new DeviceHotplugEventListener() {
            @Override
            public void onDevicePlugged(Device device) {
                if (device == null) {
                    LOGGER.error("Device 2.x plugged but got null device");
                    // Send 'failure' event on bus
                    eventBus.send("storyteller.failure", null);
                } else {
                    LOGGER.info("Device 2.x plugged");
                    StoryTellerService.this.fsDevice = device;
                    CompletableFuture.runAsync(() -> fsDriver.getDeviceInfos()
                            .handle((infos, e) -> {
                                if (e != null) {
                                    LOGGER.error("Failed to plug device 2.x", e);
                                    // Send 'failure' event on bus
                                    eventBus.send("storyteller.failure", null);
                                } else {
                                    JsonObject eventData = new JsonObject()
                                            .put("uuid", SecurityUtils.encodeHex(infos.getUuid()))
                                            .put("serial", infos.getSerialNumber())
                                            .put("firmware", infos.getFirmwareMajor() + "." + infos.getFirmwareMinor())
                                            .put("storage", new JsonObject()
                                                    .put("size", infos.getSdCardSizeInBytes())
                                                    .put("free", infos.getSdCardSizeInBytes() - infos.getUsedSpaceInBytes())
                                                    .put("taken", infos.getUsedSpaceInBytes())
                                            )
                                            .put("error", false)
                                            .put("driver", "fs");
                                    // Send 'plugged' event on bus
                                    eventBus.send("storyteller.plugged", eventData);
                                }
                                return null;
                            })
                    );
                }
            }

            @Override
            public void onDeviceUnplugged(Device device) {
                LOGGER.info("Device 2.x unplugged");
                StoryTellerService.this.fsDevice = null;
                // Send 'unplugged' event on bus
                eventBus.send("storyteller.unplugged", null);
            }
        });
    }

    public CompletableFuture<Optional<JsonObject>> deviceInfos() {
        if (device != null) {
            return deviceInfosV1();
        } else if (fsDevice != null) {
            return deviceInfosV2();
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
    private CompletableFuture<Optional<JsonObject>> deviceInfosV1() {
        return driver.getDeviceInfos()
                .thenApply(infos -> Optional.of(
                        new JsonObject()
                                .put("uuid", infos.getUuid().toString())
                                .put("serial", infos.getSerialNumber())
                                .put("firmware", infos.getFirmwareMajor() == -1 ? null : infos.getFirmwareMajor() + "." + infos.getFirmwareMinor())
                                .put("storage", new JsonObject()
                                        .put("size", infos.getSdCardSizeInSectors() * (long) LibUsbMassStorageHelper.SECTOR_SIZE)
                                        .put("free", (infos.getSdCardSizeInSectors() - infos.getUsedSpaceInSectors()) * (long) LibUsbMassStorageHelper.SECTOR_SIZE)
                                        .put("taken", infos.getUsedSpaceInSectors() * (long) LibUsbMassStorageHelper.SECTOR_SIZE)
                                )
                                .put("error", infos.isInError())
                                .put("driver", "raw")
                        )
                );
    }
    private CompletableFuture<Optional<JsonObject>> deviceInfosV2() {
        return fsDriver.getDeviceInfos()
                .thenApply(infos -> Optional.of(
                        new JsonObject()
                                .put("uuid", SecurityUtils.encodeHex(infos.getUuid()))
                                .put("serial", infos.getSerialNumber())
                                .put("firmware", infos.getFirmwareMajor() + "." + infos.getFirmwareMinor())
                                .put("storage", new JsonObject()
                                        .put("size", infos.getSdCardSizeInBytes())
                                        .put("free", infos.getSdCardSizeInBytes() - infos.getUsedSpaceInBytes())
                                        .put("taken", infos.getUsedSpaceInBytes())
                                )
                                .put("error", false)
                                .put("driver", "fs")
                        )
                );
    }

    public CompletableFuture<JsonArray> packs() {
        if (device != null) {
            return packsV1();
        } else if (fsDevice != null) {
            return packsV2();
        } else {
            return CompletableFuture.completedFuture(new JsonArray());
        }
    }
    private CompletableFuture<JsonArray> packsV1() {
        return driver.getPacksList()
                .thenApply(packs ->
                        new JsonArray(
                                packs.stream()
                                        .map(this::getRawPackMetadata)
                                        .collect(Collectors.toList())
                        )
                );
    }
    private CompletableFuture<JsonArray> packsV2() {
        return fsDriver.getPacksList()
                .thenApply(packs ->
                        new JsonArray(
                                packs.stream()
                                        .map(this::getFsPackMetadata)
                                        .collect(Collectors.toList())
                        )
                );
    }

    public CompletableFuture<Optional<String>> addPack(String uuid, Path packFile) {
        if (device != null) {
            return addPackV1(uuid, packFile);
        } else if (fsDevice != null) {
            return addPackV2(uuid, packFile);
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
    private CompletableFuture<Optional<String>> addPackV1(String uuid, Path packFile) {
        // Check that the pack is not already on the device
        return driver.getPacksList()
                .thenApply(packs -> {
                    // Look for UUID in packs index
                    Optional<RawStoryPackInfos> matched = packs.stream().filter(p -> p.getUuid().equals(UUID.fromString(uuid))).findFirst();
                    if (matched.isPresent()) {
                        LOGGER.error("Cannot add pack to device because the pack already exists on the device");
                        return Optional.empty();
                    } else {

                        String transferId = UUID.randomUUID().toString();
                        try {
                            // Create stream on file
                            long packSize = Files.size(packFile);
                            LOGGER.info("Transferring pack to device: " + FileUtils.readableByteSize(packSize));
                            int fileSizeInSectors = (int) (packSize / LibUsbMassStorageHelper.SECTOR_SIZE);

                            LOGGER.info("Transferring pack to device: " + fileSizeInSectors + " sectors");
                            try(InputStream is = new BufferedInputStream(Files.newInputStream(packFile)) ){
                                driver.uploadPack(is, fileSizeInSectors, new TransferProgressListener() {
                                    @Override
                                    public void onProgress(TransferStatus status) {
                                        // Send event on eventbus to monitor progress
                                        double p = (double) status.getTransferred() / (double) status.getTotal();
                                        LOGGER.debug("Pack add progress... " + status.getTransferred() + " / " + status.getTransferred() + " (" + p + ")");
                                        eventBus.send("storyteller.transfer." + transferId + ".progress", new JsonObject().put("progress", p));
                                    }
    
                                    @Override
                                    public void onComplete(TransferStatus status) {
                                        LOGGER.info("Pack added.");
                                    }
                                }).whenComplete((status, t) -> {
                                    // Handle failure
                                    if (t != null) {
                                        LOGGER.error("Failed to add pack to device", t);
                                        // Send event on eventbus to signal transfer failure
                                        eventBus.send("storyteller.transfer." + transferId + ".done", new JsonObject().put("success", false));
                                    }
                                    // Handle success
                                    else {
                                        // Send event on eventbus to signal end of transfer
                                        eventBus.send("storyteller.transfer." + transferId + ".done", new JsonObject().put("success", true));
                                    }
                                });
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to add pack to device", e);
                            // Send event on eventbus to signal transfer failure
                            eventBus.send("storyteller.transfer." + transferId + ".done", new JsonObject().put("success", false));
                        }
                        return Optional.of(transferId);
                    }
                });
    }
    private CompletableFuture<Optional<String>> addPackV2(String uuid, Path packFile) {
        // Check that the pack is not already on the device
        return fsDriver.getPacksList()
                .thenApply(packs -> {
                    // Look for UUID in packs index
                    Optional<FsStoryPackInfos> matched = packs.stream().filter(p -> p.getUuid().equals(UUID.fromString(uuid))).findFirst();
                    if (matched.isPresent()) {
                        LOGGER.error("Cannot add pack to device because the pack already exists on the device");
                        return Optional.empty();
                    } else {
                        String transferId = UUID.randomUUID().toString();
                        try {
                            LOGGER.info("Transferring pack folder to device: " + packFile);
                            fsDriver.uploadPack(uuid, packFile, new TransferProgressListener() {
                                @Override
                                public void onProgress(TransferStatus status) {
                                    // Send event on eventbus to monitor progress
                                    double p = (double) status.getTransferred() / (double) status.getTotal();
                                    LOGGER.debug("Pack add progress... " + status.getTransferred() + " / " + status.getTransferred() + " (" + p + ")");
                                    eventBus.send("storyteller.transfer." + transferId + ".progress", new JsonObject().put("progress", p));
                                }

                                @Override
                                public void onComplete(TransferStatus status) {
                                    LOGGER.info("Pack added.");
                                }
                            }).whenComplete((status, t) -> {
                                // Handle failure
                                if (t != null) {
                                    LOGGER.error("Failed to add pack to device", t);
                                    // Send event on eventbus to signal transfer failure
                                    eventBus.send("storyteller.transfer." + transferId + ".done", new JsonObject().put("success", false));
                                }
                                // Handle success
                                else {
                                    // Send event on eventbus to signal end of transfer
                                    eventBus.send("storyteller.transfer." + transferId + ".done", new JsonObject().put("success", true));
                                }
                            });
                        } catch (Exception e) {
                            LOGGER.error("Failed to add pack to device", e);
                            // Send event on eventbus to signal transfer failure
                            eventBus.send("storyteller.transfer." + transferId + ".done", new JsonObject().put("success", false));
                        }
                        return Optional.of(transferId);
                    }
                });
    }

    public CompletableFuture<Boolean> deletePack(String uuid) {
        if (device != null) {
            return driver.deletePack(uuid);
        } else if (fsDevice != null) {
            return fsDriver.deletePack(uuid);
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Boolean> reorderPacks(List<String> uuids) {
        if (device != null) {
            return driver.reorderPacks(uuids);
        } else if (fsDevice != null) {
            return fsDriver.reorderPacks(uuids);
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Optional<String>> extractPack(String uuid, Path packFile) {
        if (device != null) {
            return extractPackV1(uuid, packFile);
        } else if (fsDevice != null) {
            return extractPackV2(uuid, packFile);
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
    private CompletableFuture<Optional<String>> extractPackV1(String uuid, Path destFile) {
        String transferId = UUID.randomUUID().toString();
        // Check that the destination is available
        if (Files.exists(destFile)) {
            LOGGER.error("Cannot extract pack from device because the destination file already exists");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        // Open destination file
        try (OutputStream fos = new BufferedOutputStream(Files.newOutputStream(destFile)) ){
            driver.downloadPack(uuid, fos, new TransferProgressListener() {
                @Override
                public void onProgress(TransferStatus status) {
                    // Send event on eventbus to monitor progress
                    double p = (double) status.getTransferred() / (double) status.getTotal();
                    LOGGER.debug("Pack extraction progress... " + status.getTransferred() + " / " + status.getTotal() + " (" + p + ")");
                    eventBus.send("storyteller.transfer."+transferId+".progress", new JsonObject().put("progress", p));
                }
                @Override
                public void onComplete(TransferStatus status) {
                    LOGGER.info("Pack extracted.");
                }
            }).whenComplete((status,t) -> {
                // Handle failure
                if (t != null) {
                    LOGGER.error("Failed to extract pack from device", t);
                    // Send event on eventbus to signal transfer failure
                    eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                }
                // Handle success
                else {
                    // Send event on eventbus to signal end of transfer
                    eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", true));
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to extract pack from device", e);
            // Send event on eventbus to signal transfer failure
            eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
        }
        return CompletableFuture.completedFuture(Optional.of(transferId));
    }
    private CompletableFuture<Optional<String>> extractPackV2(String uuid, Path destFile) {
        String transferId = UUID.randomUUID().toString();
        // Check that the destination is available
        if (Files.exists(destFile.resolve(uuid))) {
            LOGGER.error("Cannot extract pack from device because the destination file already exists");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            fsDriver.downloadPack(uuid, destFile.toAbsolutePath().toString(), new TransferProgressListener() {
                @Override
                public void onProgress(TransferStatus status) {
                    // Send event on eventbus to monitor progress
                    double p = (double) status.getTransferred() / (double) status.getTotal();
                    LOGGER.debug("Pack extraction progress... " + status.getTransferred() + " / " + status.getTotal() + " (" + p + ")");
                    eventBus.send("storyteller.transfer."+transferId+".progress", new JsonObject().put("progress", p));
                }
                @Override
                public void onComplete(TransferStatus status) {
                    LOGGER.info("Pack extracted.");
                }
            }).whenComplete((status,t) -> {
                // Handle failure
                if (t != null) {
                    LOGGER.error("Failed to extract pack from device", t);
                    // Send event on eventbus to signal transfer failure
                    eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                }
                // Handle success
                else {
                    // Send event on eventbus to signal end of transfer
                    eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", true));
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to extract pack from device", e);
            // Send event on eventbus to signal transfer failure
            eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
        }
        return CompletableFuture.completedFuture(Optional.of(transferId));
    }

    public CompletableFuture<Void> dump(Path outputPath) {
        if (this.device == null) {
            return CompletableFuture.completedFuture(null);
        }
        return driver.dump(outputPath);
    }

    private JsonObject getRawPackMetadata(RawStoryPackInfos pack) {
        return databaseMetadataService.getPackMetadata(pack.getUuid().toString())
                .map(metadata -> new JsonObject()
                        .put("uuid", pack.getUuid().toString())
                        .put("format", Constants.PACK_FORMAT_RAW)
                        .put("version", pack.getVersion())
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("sectorSize", pack.getSizeInSectors())
                        .put("official", metadata.isOfficial())
                )
                .orElse(new JsonObject()
                        .put("uuid", pack.getUuid().toString())
                        .put("format", Constants.PACK_FORMAT_RAW)
                        .put("version", pack.getVersion())
                        .put("sectorSize", pack.getSizeInSectors())
                );
    }
    private JsonObject getFsPackMetadata(FsStoryPackInfos pack) {
        return databaseMetadataService.getPackMetadata(pack.getUuid().toString())
                .map(metadata -> new JsonObject()
                        .put("uuid", pack.getUuid().toString())
                        .put("format", Constants.PACK_FORMAT_FS)
                        .put("version", pack.getVersion())
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("folderName", pack.getFolderName())
                        .put("sizeInBytes", pack.getSizeInBytes())
                        .put("official", metadata.isOfficial())
                        .put("nightModeAvailable", pack.isNightModeAvailable())
                )
                .orElse(new JsonObject()
                        .put("uuid", pack.getUuid().toString())
                        .put("format", Constants.PACK_FORMAT_FS)
                        .put("version", pack.getVersion())
                        .put("folderName", pack.getFolderName())
                        .put("sizeInBytes", pack.getSizeInBytes())
                );
    }

}
