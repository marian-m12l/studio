/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import com.lunii.device.gateway.DriverException;
import com.lunii.device.wrapper.*;
import com.lunii.device.wrapper.model.StoryTeller;
import com.lunii.device.wrapper.model.StoryTellerPack;
import com.lunii.java.util.progress.Progress;
import com.lunii.java.util.progress.ProgressCallback;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.stream.Collectors;

public class StoryTellerService {

    private final Logger LOGGER = LoggerFactory.getLogger(StoryTellerService.class);

    private final EventBus eventBus;

    private final DatabaseMetadataService databaseMetadataService;

    private StoryTellerHandler storyTellerHandler;


    public StoryTellerService(EventBus eventBus, DatabaseMetadataService databaseMetadataService) {
        this.eventBus = eventBus;
        this.databaseMetadataService = databaseMetadataService;

        LOGGER.info("Setting up story teller driver");
        StoryTellerDriver driver = new StoryTellerDriver();

        driver.addDevicePluggedListener(new StoryTellerDriver.DevicePluggedListener() {
            public void onPlugged(StoryTellerHandler handler) {
                if (handler == null) {
                    LOGGER.error("Device plugged but got null handler");
                    // Send 'failure' event on bus
                    eventBus.send("storyteller.failure", null);
                } else {
                    LOGGER.info("Device plugged");
                    storyTellerHandler = handler;
                    StoryTeller storyTeller = storyTellerHandler.getStoryTeller();
                    JsonObject eventData = new JsonObject()
                            .put("uuid", storyTeller.getUUID())
                            .put("serial", storyTeller.getSerialNumber())
                            .put("firmware", storyTeller.getFirmwareVersion())
                            .put("storage", new JsonObject()
                                    .put("size", storyTeller.getSdCardSize())
                                    .put("free", storyTellerHandler.getTotalFreeSpaceFromSD())
                                    .put("taken", storyTellerHandler.getTotalTakenSpaceFromSD())
                            )
                            .put("error", storyTeller.isError());
                    // Send 'plugged' event on bus
                    eventBus.send("storyteller.plugged", eventData);
                }

            }
            public void onPluggedFailed(DriverException driverException) {
                LOGGER.error("Failed to plug device");
                // Send 'failure' event on bus
                eventBus.send("storyteller.failure", null);
            }
        });

        driver.addDeviceUnpluggedListener(new StoryTellerDriver.DeviceUnpluggedListener() {
            @Override
            public void onUnplugged(StoryTellerHandler handler) {
                LOGGER.info("Device unplugged");
                storyTellerHandler = null;
                // Send 'unplugged' event on bus
                eventBus.send("storyteller.unplugged", null);
            }
        });
    }

    public Optional<JsonObject> deviceInfos() {
        if (storyTellerHandler == null) {
            return Optional.empty();
        } else {
            StoryTeller storyTeller = storyTellerHandler.getStoryTeller();
            return Optional.of(new JsonObject()
                    .put("uuid", storyTeller.getUUID())
                    .put("serial", storyTeller.getSerialNumber())
                    .put("firmware", storyTeller.getFirmwareVersion())
                    .put("storage", new JsonObject()
                            .put("size", storyTeller.getSdCardSize())
                            .put("free", storyTellerHandler.getTotalFreeSpaceFromSD())
                            .put("taken", storyTellerHandler.getTotalTakenSpaceFromSD())
                    )
                    .put("error", storyTeller.isError())
            );
        }
    }

    public JsonArray packs() {
        if (storyTellerHandler == null) {
            return new JsonArray();
        } else {
            try {
                return new JsonArray(
                        storyTellerHandler.getStoriesPacks().stream()
                                .map(this::getPackMetadata)
                                .collect(Collectors.toList())
                );
            } catch (DriverException | InconsistentMemoryException e) {
                LOGGER.error("Failed to read packs from device", e);
                throw new RuntimeException(e);
            }
        }
    }

    public Optional<String> addPack(File packFile) {
        if (storyTellerHandler == null) {
            return Optional.empty();
        } else {
            String transferId = UUID.randomUUID().toString();
            // Perform transfer asynchronously, and send events on eventbus to monitor progress and end of transfer
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        storyTellerHandler.addStoryPack(-1, packFile, new ProgressCallback() {
                            @Override
                            public boolean onProgress(Progress progress) {
                                // Send events on eventbus to monitor progress
                                double p = progress.getWorkDone().doubleValue() / progress.getTotalWork().doubleValue();
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
            }, 0);
            return Optional.of(transferId);
        }
    }

    public boolean deletePack(String uuid) {
        if (storyTellerHandler == null) {
            return false;
        } else {
            try {
                if (storyTellerHandler.getStoriesPacks().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(uuid))) {
                    storyTellerHandler.deletePack(uuid);
                    return true;
                } else {
                    LOGGER.error("Cannot remove pack from device because it is not on the device");
                    return false;
                }
            } catch (DriverException | InconsistentMemoryException | PackDoesNotExistException e) {
                LOGGER.error("Failed to remove pack from device", e);
                e.printStackTrace();
                return false;
            }
        }
    }

    private JsonObject getPackMetadata(StoryTellerPack pack) {
        return databaseMetadataService.getPackMetadata(pack.getUuid())
                .map(metadata -> new JsonObject()
                        .put("uuid", pack.getUuid())
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("sectorSize", pack.getSectorSize())
                        .put("official", metadata.isOfficial())
                )
                .orElse(new JsonObject()
                        .put("uuid", pack.getUuid())
                        .put("sectorSize", pack.getSectorSize())
                );
    }

}
