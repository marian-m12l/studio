/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import com.google.gson.JsonParser;
import com.lunii.device.gateway.DriverException;
import com.lunii.device.wrapper.InconsistentMemoryException;
import com.lunii.device.wrapper.StoryTellerDriver;
import com.lunii.device.wrapper.StoryTellerHandler;
import com.lunii.device.wrapper.model.StoryTeller;
import com.lunii.device.wrapper.model.StoryTellerPack;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Optional;
import java.util.stream.Collectors;

public class StoryTellerService {

    public static final String STORAGE_ROOT = "https://storage.googleapis.com/lunii-data-prod";
    public static final String PROP_DB_OFFICIAL = "studio.db.official";
    public static final String PATH_DB_OFFICIAL_JSON = ".studio/db/official.json";

    private final Logger LOGGER = LoggerFactory.getLogger(StoryTellerService.class);

    private final EventBus eventBus;

    private StoryTellerHandler storyTellerHandler;

    public StoryTellerService(EventBus eventBus) {
        this.eventBus = eventBus;

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

    private JsonObject getPackMetadata(StoryTellerPack pack) {
        JsonParser parser = new JsonParser();
        try {
            // Fetch from official metadata database file (path may be overridden by system property `studio.db.official`)
            String databasePath = System.getProperty(PROP_DB_OFFICIAL, System.getProperty("user.home") + PATH_DB_OFFICIAL_JSON);
            com.google.gson.JsonObject root = parser.parse(new FileReader(databasePath)).getAsJsonObject();
            Optional<String> maybePackKey = root.keySet().stream().filter(key -> root.getAsJsonObject(key).get("uuid").getAsString().equalsIgnoreCase(pack.getUuid())).findFirst();
            if (maybePackKey.isPresent()) {
                com.google.gson.JsonObject packMetadata = root.getAsJsonObject(maybePackKey.get());
                return new JsonObject()
                        .put("uuid", pack.getUuid())
                        .put("title", packMetadata.get("title").getAsString())
                        .put("description", packMetadata.get("description").getAsString())
                        .put("image", STORAGE_ROOT + packMetadata.getAsJsonObject("image").get("image_url").getAsString())
                        .put("sectorSize", pack.getSectorSize());
            }
        } catch (FileNotFoundException e) {
            LOGGER.error("Missing official metadata database file", e);
        }

        // Missing metadata
        return new JsonObject()
                .put("uuid", pack.getUuid())
                .put("sectorSize", pack.getSectorSize());
    }

}
