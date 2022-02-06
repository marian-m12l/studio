/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import java.nio.file.Path;
import java.util.List;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import studio.core.v1.Constants;
import studio.core.v1.utils.PackFormat;
import studio.webui.service.IStoryTellerService;
import studio.webui.service.LibraryService;

public class DeviceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceController.class);

    private DeviceController() {
        throw new IllegalArgumentException("Utility class");
    }

    public static Router apiRouter(Vertx vertx, IStoryTellerService storyTellerService) {
        Router router = Router.router(vertx);

        // Plugged device metadata
        router.get("/infos").handler(ctx -> {
            storyTellerService.deviceInfos() //
                    .whenComplete((maybeDeviceInfos, e) -> {
                        if (e != null) {
                            LOGGER.error("Failed to read device infos", e);
                            ctx.fail(500, e);
                        } else {
                            maybeDeviceInfos.ifPresentOrElse(
                                    deviceInfos -> ctx.response()
                                            .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                                            .end(Json.encode(deviceInfos.put("plugged", true))),
                                    () -> ctx.response()
                                            .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                                            .end(Json.encode(new JsonObject().put("plugged", false)))
                            );
                        }
                    });
        });

        // Plugged device packs list
        router.get("/packs").handler(ctx -> {
            storyTellerService.packs() //
                    .whenComplete((devicePacks, e) -> {
                        if (e != null) {
                            LOGGER.error("Failed to read packs from device", e);
                            ctx.fail(500, e);
                        } else {
                            ctx.response()
                                    .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                                    .end(Json.encode(devicePacks));
                        }
                    });
        });

        // Add pack from library to device
        router.post("/addFromLibrary").blockingHandler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            String packPath = ctx.getBodyAsJson().getString("path");
            Path packFile = LibraryService.libraryPath().resolve(packPath);
            // Start transfer to device
            storyTellerService.addPack(uuid, packFile) //
                    .whenComplete((maybeTransferId, e) -> {
                        if (e != null) {
                            LOGGER.error("Failed to transfer pack to device", e);
                            ctx.fail(500, e);
                        } else {
                            maybeTransferId
                                    .ifPresentOrElse(
                                            transferId ->
                                                    // Return the transfer id, which is used to monitor transfer progress
                                                    ctx.response()
                                                            .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                                                            .end(Json.encode(new JsonObject().put("transferId", transferId))),
                                            () -> {
                                                LOGGER.error("Failed to transfer pack to device");
                                                ctx.fail(500);
                                            }
                                    );
                        }
                    });
        });

        // Remove pack from device
        router.post("/removeFromDevice").handler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            storyTellerService.deletePack(uuid) //
                    .whenComplete((removed, e) -> {
                        if (e != null) {
                            LOGGER.error("Failed to remove pack from device", e);
                            ctx.fail(500, e);
                        } else {
                            if (removed) {
                                ctx.response()
                                        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                                        .end(Json.encode(new JsonObject().put("success", true)));
                            } else {
                                LOGGER.error("Pack was not removed from device");
                                ctx.fail(500);
                            }
                        }
                    });
        });

        // Reorder packs on device
        router.post("/reorder").handler(ctx -> {
            @SuppressWarnings("unchecked")
            List<String> uuids = ctx.getBodyAsJson().getJsonArray("uuids").getList();
            storyTellerService.reorderPacks(uuids) //
                    .whenComplete((reordered, e) -> {
                        if (e != null) {
                            LOGGER.error("Failed to reorder packs on device", e);
                            ctx.fail(500, e);
                        } else {
                            if (reordered) {
                                ctx.response()
                                        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                                        .end(Json.encode(new JsonObject().put("success", true)));
                            } else {
                                LOGGER.error("Failed to reorder packs on device");
                                ctx.fail(500);
                            }
                        }
                    });
        });

        // Add pack from device to library
        router.post("/addToLibrary").handler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            String driver = ctx.getBodyAsJson().getString("driver");
            // Transfer pack file to library file
            Path path = null;
            if (PackFormat.RAW.name().equalsIgnoreCase(driver)) {
                path = LibraryService.libraryPath().resolve(uuid + ".pack");
            } else if (PackFormat.FS.name().equalsIgnoreCase(driver)) {
                path = LibraryService.libraryPath();
            } else {
                ctx.fail(400);
                return;
            }
            storyTellerService.extractPack(uuid, path) //
                    .whenComplete((maybeTransferId, e) -> {
                        if (e != null) {
                            LOGGER.error("Failed to transfer pack from device", e);
                            ctx.fail(500, e);
                        } else {
                            maybeTransferId
                                    .ifPresentOrElse(
                                            transferId ->
                                                    // Return the transfer id, which is used to monitor transfer progress
                                                    ctx.response()
                                                            .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                                                            .end(Json.encode(new JsonObject().put("transferId", transferId))),
                                            () -> {
                                                LOGGER.error("Failed to transfer pack from device");
                                                ctx.fail(500);
                                            }
                                    );
                        }
                    });
        });

        // Dump important sectors
        router.post("/dump").handler(ctx -> {
            String outputPath = ctx.getBodyAsJson().getString("outputPath");
            // Dump important sector into outputPath
            storyTellerService.dump(Path.of(outputPath)) //
                    .whenComplete((done, e) -> {
                        if (e != null) {
                            LOGGER.error("Failed to dump important sectors from device", e);
                            ctx.fail(500, e);
                        } else {
                            ctx.response()
                                    .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                                    .end(Json.encode(new JsonObject().put("success", true)));
                        }
                    });
        });

        return router;
    }
}
