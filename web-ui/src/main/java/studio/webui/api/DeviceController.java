/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import studio.webui.service.IStoryTellerService;
import studio.webui.service.LibraryService;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class DeviceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceController.class);


    public static Router apiRouter(Vertx vertx, IStoryTellerService storyTellerService, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Plugged device metadata
        router.get("/infos").blockingHandler(ctx -> {
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
        router.get("/packs").blockingHandler(ctx -> {
            JsonArray devicePacks = storyTellerService.packs();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(devicePacks));
        });

        // Add pack from library to device
        router.post("/addFromLibrary").blockingHandler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            String packPath = ctx.getBodyAsJson().getString("path");
            Boolean allowEnriched = ctx.getBodyAsJson().getBoolean("allowEnriched", false);
            // First, get the pack file, potentially converted from archive format to pack format
            // Perform conversion/uncompression asynchronously
            Future<File> futureConvertedPack = Future.future();
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    libraryService.getBinaryPackFile(packPath, allowEnriched)
                            .ifPresentOrElse(
                                    packFile -> futureConvertedPack.tryComplete(packFile),
                                    () -> futureConvertedPack.tryFail("Failed to read or convert pack"));
                    futureConvertedPack.tryComplete();
                }
            }, 1000);

            futureConvertedPack.setHandler(maybeConvertedPack -> {
                if (maybeConvertedPack.succeeded()) {
                    // Then, start transfer to device
                    storyTellerService.addPack(uuid, maybeConvertedPack.result())
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
                            );
                } else {
                    LOGGER.error("Failed to read or convert pack");
                    ctx.fail(500, maybeConvertedPack.cause());
                }
            });
        });

        // Remove pack from device
        router.post("/removeFromDevice").blockingHandler(ctx -> {
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

        // Reorder packs on device
        router.post("/reorder").blockingHandler(ctx -> {
            List<String> uuids = ctx.getBodyAsJson().getJsonArray("uuids").getList();
            boolean reordered = storyTellerService.reorderPacks(uuids);
            if (reordered) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject().put("success", true)));
            } else {
                LOGGER.error("Failed to reorder packs on device");
                ctx.fail(500);
            }
        });

        // Add pack from device to library
        router.post("/addToLibrary").blockingHandler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            // Transfer pack file to library file
            String path = libraryService.libraryPath() + uuid + ".pack";
            storyTellerService.extractPack(uuid, new File(path))
                    .ifPresentOrElse(
                            transferId ->
                                    // Return the transfer id, which is used to monitor transfer progress
                                    ctx.response()
                                            .putHeader("content-type", "application/json")
                                            .end(Json.encode(new JsonObject().put("transferId", transferId))),
                            () -> {
                                LOGGER.error("Failed to transfer pack from device");
                                ctx.fail(500);
                            }
                    );
        });

        return router;
    }
}
