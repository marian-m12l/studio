/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import studio.webui.service.LibraryService;
import studio.webui.service.StoryTellerService;

import java.util.Optional;

public class DeviceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceController.class);


    public static Router apiRouter(Vertx vertx, StoryTellerService storyTellerService, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Plugged device metadata
        router.get("/infos").handler(ctx -> {
            Optional<JsonObject> maybeDeviceInfos = storyTellerService.deviceInfos();
            maybeDeviceInfos.ifPresentOrElse(
                    deviceInfos -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(deviceInfos.put("plugged", true))),
                    () -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(new JsonObject().put("plugged", false)))
            );
        });

        // Plugged device packs list
        router.get("/packs").handler(ctx -> {
            JsonArray devicePacks = storyTellerService.packs();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(devicePacks));
        });

        // Add pack from library to device
        router.post("/addFromLibrary").handler(ctx -> {
            String packPath = ctx.getBodyAsJson().getString("path");
            // First, get the pack file, potentially converted from archive format to pack format
            libraryService.getPackFile(packPath)
                    .ifPresentOrElse(
                            packFile ->
                                    // Then, start transfer to device
                                    storyTellerService.addPack(packFile)
                                            .ifPresentOrElse(
                                                    transferId ->
                                                            // Return the transfer id, which is used to monitor transfer progress
                                                            ctx.response()
                                                                    .putHeader("content-type", "application/json")
                                                                    .end(Json.encode(new JsonObject().put("transferId", transferId))),
                                                    () -> {
                                                        LOGGER.error("Failed to transfer pack to device");
                                                        ctx.fail(500);
                                                    }
                                            ),
                            () -> {
                                LOGGER.error("Failed to read or convert pack");
                                ctx.fail(500);
                            });
        });

        // Remove pack from device
        router.post("/removeFromDevice").handler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            boolean removed = storyTellerService.deletePack(uuid);
            if (removed) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject().put("success", true)));
            } else {
                LOGGER.error("Pack was not removed from device");
                ctx.fail(500);
            }
        });

        return router;
    }
}
