/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.usb4java.Device;
import studio.driver.LibUsbHelper;
import studio.driver.StoryTellerAsyncDriver;
import studio.driver.event.DeviceHotplugEventListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.model.StoryPackInfos;
import studio.driver.model.TransferStatus;
import studio.metadata.DatabaseMetadataService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class StoryTellerService implements IStoryTellerService {

    private final Logger LOGGER = LoggerFactory.getLogger(StoryTellerService.class);

    private final EventBus eventBus;

    private final DatabaseMetadataService databaseMetadataService;

    private StoryTellerAsyncDriver driver;
    private Device device;


    public StoryTellerService(EventBus eventBus, DatabaseMetadataService databaseMetadataService) {
        this.eventBus = eventBus;
        this.databaseMetadataService = databaseMetadataService;

        LOGGER.info("Setting up story teller driver");
        driver = new StoryTellerAsyncDriver();

        driver.registerDeviceListener(new DeviceHotplugEventListener() {
            @Override
            public void onDevicePlugged(Device device) {
                if (device == null) {
                    LOGGER.error("Device plugged but got null device");
                    // Send 'failure' event on bus
                    eventBus.send("storyteller.failure", null);
                } else {
                    LOGGER.info("Device plugged");
                    StoryTellerService.this.device = device;
                    CompletableFuture.runAsync(() -> driver.getDeviceInfos()
                            .handle((infos, e) -> {
                                if (e != null) {
                                    LOGGER.error("Failed to plug device", e);
                                    // Send 'failure' event on bus
                                    eventBus.send("storyteller.failure", null);
                                } else {
                                    JsonObject eventData = new JsonObject()
                                            .put("uuid", infos.getUuid().toString())
                                            .put("serial", infos.getSerialNumber())
                                            .put("firmware", infos.getFirmwareMajor() == -1 ? null : infos.getFirmwareMajor() + "." + infos.getFirmwareMinor())
                                            .put("storage", new JsonObject()
                                                    .put("size", infos.getSdCardSizeInSectors() * (long) LibUsbHelper.SECTOR_SIZE)
                                                    .put("free", (infos.getSdCardSizeInSectors() - infos.getUsedSpaceInSectors()) * (long) LibUsbHelper.SECTOR_SIZE)
                                                    .put("taken", infos.getUsedSpaceInSectors() * (long) LibUsbHelper.SECTOR_SIZE)
                                            )
                                            .put("error", infos.isInError());
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
                LOGGER.info("Device unplugged");
                StoryTellerService.this.device = null;
                // Send 'unplugged' event on bus
                eventBus.send("storyteller.unplugged", null);
            }
        });
    }

    public CompletableFuture<Optional<JsonObject>> deviceInfos() {
        if (this.device == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        } else {
            return driver.getDeviceInfos()
                    .thenApply(infos -> Optional.of(
                            new JsonObject()
                                .put("uuid", infos.getUuid().toString())
                                .put("serial", infos.getSerialNumber())
                                .put("firmware", infos.getFirmwareMajor() == -1 ? null : infos.getFirmwareMajor() + "." + infos.getFirmwareMinor())
                                .put("storage", new JsonObject()
                                        .put("size", infos.getSdCardSizeInSectors() * (long) LibUsbHelper.SECTOR_SIZE)
                                        .put("free", (infos.getSdCardSizeInSectors() - infos.getUsedSpaceInSectors()) * (long) LibUsbHelper.SECTOR_SIZE)
                                        .put("taken", infos.getUsedSpaceInSectors() * (long) LibUsbHelper.SECTOR_SIZE)
                                )
                                .put("error", infos.isInError())
                        )
                    );
        }
    }

    public CompletableFuture<JsonArray> packs() {
        if (this.device == null) {
            return CompletableFuture.completedFuture(new JsonArray());
        } else {
            return driver.getPacksList()
                    .thenApply(packs ->
                            new JsonArray(
                                    packs.stream()
                                            .map(this::getPackMetadata)
                                            .collect(Collectors.toList())
                            )
                    );
        }
    }

    public Optional<String> addPack(String uuid, File packFile) {
        if (this.device == null) {
            return Optional.empty();
        } else {
            String transferId = UUID.randomUUID().toString();

            try {
                // Create stream on file
                FileInputStream fis = new FileInputStream(packFile);
                LOGGER.info("Transferring pack to device: " + packFile.length() + " bytes");
                int fileSizeInSectors = (int) (packFile.length() / LibUsbHelper.SECTOR_SIZE);
                LOGGER.info("Transferring pack to device: " + fileSizeInSectors + " sectors");
                driver.uploadPack(fis, fileSizeInSectors, new TransferProgressListener() {
                    @Override
                    public void onProgress(TransferStatus status) {
                        // Send event on eventbus to monitor progress
                        double p = (double) status.getTransferred() / (double) status.getTotal();
                        LOGGER.debug("Pack add progress... " + status.getTransferred() + " / " + status.getTransferred() + " (" + p + ")");
                        eventBus.send("storyteller.transfer."+transferId+".progress", new JsonObject().put("progress", p));
                    }
                    @Override
                    public void onComplete(TransferStatus status) {
                        LOGGER.info("Pack added.");
                    }
                }).whenComplete((status,t) -> {
                    // Close source file in all cases
                    try {
                        fis.close();
                    } catch (IOException e) {
                        LOGGER.error("Failed to close source file.", e);
                    }
                    // Handle failure
                    if (t != null) {
                        LOGGER.error("Failed to add pack to device", t);
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
                LOGGER.error("Failed to add pack to device", e);
                // Send event on eventbus to signal transfer failure
                eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
            }
            return Optional.of(transferId);
        }
    }

    public CompletableFuture<Boolean> deletePack(String uuid) {
        if (this.device == null) {
            return CompletableFuture.completedFuture(false);
        } else {
            return driver.deletePack(uuid);
        }
    }

    public CompletableFuture<Boolean> reorderPacks(List<String> uuids) {
        if (this.device == null) {
            return CompletableFuture.completedFuture(false);
        } else {
            return driver.reorderPacks(uuids);
        }
    }

    public Optional<String> extractPack(String uuid, File destFile) {
        if (this.device == null) {
            return Optional.empty();
        } else {
            String transferId = UUID.randomUUID().toString();
            // Check that the destination is available
            if (destFile.exists()) {
                LOGGER.error("Cannot extract pack from device because the destination file already exists");
                eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                return Optional.empty();
            }
            try {
                // Open destination file
                FileOutputStream fos = new FileOutputStream(destFile);
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
                    // Close destination file in all cases
                    try {
                        fos.close();
                    } catch (IOException e) {
                        LOGGER.error("Failed to close destination file.", e);
                    }
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
            return Optional.of(transferId);
        }
    }

    public CompletableFuture<Void> dump(String outputPath) {
        if (this.device == null) {
            return CompletableFuture.completedFuture(null);
        } else {
            new File(outputPath).mkdirs();
            return driver.dump(outputPath);
        }
    }

    private JsonObject getPackMetadata(StoryPackInfos pack) {
        return databaseMetadataService.getPackMetadata(pack.getUuid().toString())
                .map(metadata -> new JsonObject()
                        .put("uuid", pack.getUuid().toString())
                        .put("version", pack.getVersion())
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("sectorSize", pack.getSizeInSectors())
                        .put("official", metadata.isOfficial())
                )
                .orElse(new JsonObject()
                        .put("uuid", pack.getUuid().toString())
                        .put("version", pack.getVersion())
                        .put("sectorSize", pack.getSizeInSectors())
                );
    }

}
