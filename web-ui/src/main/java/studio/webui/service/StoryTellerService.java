/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import com.lunii.device.gateway.raw.AbstractUsb4JavaLuniiDriver;
import com.lunii.device.gateway.raw.Usb4JavaLuniiDevice;
import com.lunii.device.gateway.raw.Usb4JavaLuniiDriver;
import com.lunii.device.wrapper.raw.RawDevice;
import com.lunii.device.wrapper.raw.handler.*;
import com.lunii.java.util.progress.Progress;
import com.lunii.java.util.progress.ProgressCallback;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.core.v1.Constants;
import studio.metadata.DatabaseMetadataService;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class StoryTellerService implements IStoryTellerService {

    private final Logger LOGGER = LoggerFactory.getLogger(StoryTellerService.class);

    private final EventBus eventBus;

    private final DatabaseMetadataService databaseMetadataService;

    private RawDeviceHandler rawDeviceHandler;
    private Device rawDevice;


    public StoryTellerService(EventBus eventBus, DatabaseMetadataService databaseMetadataService) {
        this.eventBus = eventBus;
        this.databaseMetadataService = databaseMetadataService;

        LOGGER.info("Setting up story teller driver");
        Usb4JavaLuniiDriver driver = new Usb4JavaLuniiDriver();

        driver.addDevicePluggedListener(new AbstractUsb4JavaLuniiDriver.DevicePluggedListener() {
            public void onPlugged(Usb4JavaLuniiDevice device) {
                if (device == null) {
                    LOGGER.error("Device plugged but got null device");
                    // Send 'failure' event on bus
                    eventBus.send("storyteller.failure", null);
                } else {
                    try {
                        LOGGER.info("Device plugged");
                        rawDeviceHandler = new RawDeviceHandler(device);
                        rawDevice = rawDeviceHandler.load();
                        JsonObject eventData = new JsonObject()
                                .put("uuid", rawDevice.getUuid().toString())
                                .put("serial", rawDevice.getSerialNumber())
                                .put("firmware", Optional.ofNullable(rawDevice.getFirmwareVersion()).map(fv -> fv.getMajor() + "." + fv.getMinor()).orElse(null))
                                .put("storage", new JsonObject()
                                        .put("size", rawDevice.getUsableSDCardSizeInSectors()*512L)
                                        .put("free", rawDeviceHandler.getTotalFreeSpace())
                                        .put("taken", rawDeviceHandler.getTotalTakenSpace())
                                )
                                .put("error", rawDeviceHandler.getError());
                        // Send 'plugged' event on bus
                        eventBus.send("storyteller.plugged", eventData);
                    } catch (DeviceHandlerException e) {
                        LOGGER.error("Failed to plug device", e);
                        // Send 'failure' event on bus
                        eventBus.send("storyteller.failure", null);
                    }
                }

            }
        });

        driver.addDeviceUnpluggedListener(new AbstractUsb4JavaLuniiDriver.DeviceUnpluggedListener() {
            @Override
            public void onUnplugged(Usb4JavaLuniiDevice device) {
                LOGGER.info("Device unplugged");
                rawDeviceHandler = null;
                // Send 'unplugged' event on bus
                eventBus.send("storyteller.unplugged", null);
            }
        });
    }

    public Optional<JsonObject> deviceInfos() {
        if (rawDeviceHandler == null) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(new JsonObject()
                        .put("uuid", rawDevice.getUuid().toString())
                        .put("serial", rawDevice.getSerialNumber())
                        .put("firmware", Optional.ofNullable(rawDevice.getFirmwareVersion()).map(fv -> fv.getMajor() + "." + fv.getMinor()).orElse(null))
                        .put("storage", new JsonObject()
                                .put("size", rawDevice.getUsableSDCardSizeInSectors()*512L)
                                .put("free", rawDeviceHandler.getTotalFreeSpace())
                                .put("taken", rawDeviceHandler.getTotalTakenSpace())
                        )
                        .put("error", rawDeviceHandler.getError())
                );
            } catch (DeviceHandlerException e) {
                LOGGER.error("Failed to read device infos", e);
                throw new RuntimeException(e);
            }
        }
    }

    public JsonArray packs() {
        if (rawDeviceHandler == null) {
            return new JsonArray();
        } else {
            try {
                return new JsonArray(
                        rawDeviceHandler.readIndex().stream()
                                .map(this::getPackMetadata)
                                .collect(Collectors.toList())
                );
            } catch (DeviceHandlerException e) {
                LOGGER.error("Failed to read packs from device", e);
                throw new RuntimeException(e);
            }
        }
    }

    public Optional<String> addPack(String uuid, File packFile) {
        if (rawDeviceHandler == null) {
            return Optional.empty();
        } else {
            String transferId = UUID.randomUUID().toString();
            // Perform transfer asynchronously, and send events on eventbus to monitor progress and end of transfer
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        // Make sure the device does not already contain this pack
                        if (rawDeviceHandler.readIndex().stream().anyMatch(p -> p.getUuid().toString().equalsIgnoreCase(uuid))) {
                            LOGGER.error("Cannot add pack to device because the device already contains this pack");
                            eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                            return;
                        }

                        // Need to set up a virtual pack to compute the pack's start sector
                        LOGGER.info("Computing pack start sector...");
                        RawDevice virtualRawDevice = new RawDevice(rawDeviceHandler);
                        Method setErrorMethod = virtualRawDevice.getClass().getDeclaredMethod("setError", boolean.class);
                        setErrorMethod.setAccessible(true);
                        setErrorMethod.invoke(virtualRawDevice, rawDevice.isError());
                        Method setFirmwareVersionMethod = virtualRawDevice.getClass().getDeclaredMethod("setFirmwareVersion", FirmwareVersion.class);
                        setFirmwareVersionMethod.setAccessible(true);
                        setFirmwareVersionMethod.invoke(virtualRawDevice, rawDevice.getFirmwareVersion());
                        Method setSerialNumberMethod = virtualRawDevice.getClass().getDeclaredMethod("setSerialNumber", String.class);
                        setSerialNumberMethod.setAccessible(true);
                        setSerialNumberMethod.invoke(virtualRawDevice, rawDevice.getSerialNumber());
                        Method setUuidMethod = virtualRawDevice.getClass().getDeclaredMethod("setUuid", UUID.class);
                        setUuidMethod.setAccessible(true);
                        setUuidMethod.invoke(virtualRawDevice, rawDevice.getUuid());
                        Method setSdCardSizeInSectorsMethod = virtualRawDevice.getClass().getDeclaredMethod("setSdCardSizeInSectors", int.class);
                        setSdCardSizeInSectorsMethod.setAccessible(true);
                        setSdCardSizeInSectorsMethod.invoke(virtualRawDevice, rawDevice.getSdCardSizeInSectors());
                        Method setUsableSDCardSizeInSectorsMethod = virtualRawDevice.getClass().getDeclaredMethod("setUsableSDCardSizeInSectors", int.class);
                        setUsableSDCardSizeInSectorsMethod.setAccessible(true);
                        setUsableSDCardSizeInSectorsMethod.invoke(virtualRawDevice, rawDevice.getUsableSDCardSizeInSectors());
                        Method loadVirtualContentMethod = virtualRawDevice.getClass().getDeclaredMethod("loadVirtualContent", PhysicalContent.class);
                        loadVirtualContentMethod.setAccessible(true);
                        loadVirtualContentMethod.invoke(virtualRawDevice, rawDevice.physicalContent);

                        Method addPackMethod = virtualRawDevice.getRawVirtualContent().getClass().getDeclaredMethod("addPack", UUID.class, short.class, int.class, short.class);
                        addPackMethod.setAccessible(true);
                        RawVirtualPack rawVirtualPack = (RawVirtualPack) addPackMethod.invoke(virtualRawDevice.getRawVirtualContent(),
                                UUID.fromString(uuid),
                                (short) 1,  // Version, no need for exact value
                                (int) Math.ceil(packFile.length()/512.0), // Pack size in sectors
                                (short) 0   // Sampling rate, no need for exact value
                        );
                        LOGGER.info("Adding pack at start sector: " + rawVirtualPack.getStartSector());

                        rawDeviceHandler.addStoryPack(-1, packFile, rawVirtualPack.getStartSector(), new ProgressCallback() {
                            @Override
                            public boolean onProgress(Progress progress) {
                                // Send events on eventbus to monitor progress
                                double p = (double) progress.getWorkDone() / (double) progress.getTotalWork();
                                LOGGER.debug("Pack add progress... " + progress.getWorkDone() + " / " + progress.getTotalWork() + " (" + p + ")");
                                eventBus.send("storyteller.transfer."+transferId+".progress", new JsonObject().put("progress", p));
                                return true;
                            }
                        });
                        // Send event on eventbus to signal end of transfer
                        eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", true));
                    } catch (Exception e) {
                        LOGGER.error("Failed to add pack to device", e);
                        e.printStackTrace();
                        // Send event on eventbus to signal transfer failure
                        eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                    }
                }
            }, 1000);
            return Optional.of(transferId);
        }
    }

    public boolean deletePack(String uuid) {
        if (rawDeviceHandler == null) {
            return false;
        } else {
            try {
                if (rawDeviceHandler.readIndex().stream().anyMatch(p -> p.getUuid().toString().equalsIgnoreCase(uuid))) {
                    rawDeviceHandler.deleteStoryPack(UUID.fromString(uuid));
                    return true;
                } else {
                    LOGGER.error("Cannot remove pack from device because it is not on the device");
                    return false;
                }
            } catch (DeviceHandlerException | PackDoesNotExistException e) {
                LOGGER.error("Failed to remove pack from device", e);
                e.printStackTrace();
                return false;
            }
        }
    }

    public boolean reorderPacks(List<String> uuids) {
        try {
            List<PhysicalPack> physicalPacks = new ArrayList(rawDeviceHandler.readIndex());
            physicalPacks.sort(Comparator.comparingInt((o) -> uuids.indexOf(o.getUuid().toString())));
            rawDeviceHandler.writeStoryPacksIndex(physicalPacks);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to reorder packs on device", e);
            e.printStackTrace();
            return false;
        }
    }

    public Optional<String> extractPack(String uuid, File destFile) {
        if (rawDeviceHandler == null) {
            return Optional.empty();
        } else {
            String transferId = UUID.randomUUID().toString();
            // Perform transfer asynchronously, and send events on eventbus to monitor progress and end of transfer
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (rawDeviceHandler.readIndex().stream().anyMatch(p -> p.getUuid().toString().equalsIgnoreCase(uuid))) {
                            // Check that the destination is available
                            if (destFile.exists()) {
                                LOGGER.error("Cannot extract pack from device because the destination file already exists");
                                eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                                return;
                            }

                            PhysicalPack pack = rawDeviceHandler.readIndex().stream()
                                    .filter(p -> p.getUuid().toString().equalsIgnoreCase(uuid))
                                    .collect(Collectors.toList())
                                    .get(0);
                            rawDeviceHandler.writeToFileFromSD(Constants.PACKS_LIST_SECTOR+pack.getStartSector(), pack.getSectorSize(), destFile, new ProgressCallback() {
                                @Override
                                public boolean onProgress(Progress progress) {
                                    // Send events on eventbus to monitor progress
                                    double p = (double) progress.getWorkDone() / (double) progress.getTotalWork();
                                    LOGGER.debug("Pack extraction progress... " + progress.getWorkDone() + " / " + progress.getTotalWork() + " (" + p + ")");
                                    eventBus.send("storyteller.transfer."+transferId+".progress", new JsonObject().put("progress", p));
                                    return true;
                                }
                            });
                            // Send event on eventbus to signal end of transfer
                            eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", true));
                        } else {
                            LOGGER.error("Cannot extract pack from device because it is not on the device");
                            eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to extract pack from device", e);
                        e.printStackTrace();
                        // Send event on eventbus to signal transfer failure
                        eventBus.send("storyteller.transfer."+transferId+".done", new JsonObject().put("success", false));
                    }
                }
            }, 1000);
            return Optional.of(transferId);
        }
    }

    private JsonObject getPackMetadata(PhysicalPack pack) {
        return databaseMetadataService.getPackMetadata(pack.getUuid().toString())
                .map(metadata -> new JsonObject()
                        .put("uuid", pack.getUuid().toString())
                        .put("version", pack.getVersion())
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("sectorSize", pack.getSectorSize())
                        .put("official", metadata.isOfficial())
                )
                .orElse(new JsonObject()
                        .put("uuid", pack.getUuid().toString())
                        .put("version", pack.getVersion())
                        .put("sectorSize", pack.getSectorSize())
                );
    }

}
