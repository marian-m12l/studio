/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import studio.core.v1.utils.PackFormat;
import studio.webui.service.IStoryTellerService;
import studio.webui.service.LibraryService;

public class DeviceController {

    private static final Logger LOGGER = LogManager.getLogger(DeviceController.class);

    private DeviceController() {
        throw new IllegalArgumentException("Utility class");
    }

    public static Router apiRouter(Vertx vertx, IStoryTellerService storyTellerService) {
        Router router = Router.router(vertx);

        // Plugged device metadata
        router.get("/infos").handler(ctx -> Future.fromCompletionStage(storyTellerService.deviceInfos()) //
                .onFailure(e -> {
                    LOGGER.error("Failed to read device infos", e);
                    ctx.fail(500, e);
                }) //
                .onSuccess(ctx::json) //
        );

        // Plugged device packs list
        router.get("/packs").handler(ctx -> Future.fromCompletionStage(storyTellerService.packs()) //
                .onFailure(e -> {
                    LOGGER.error("Failed to read packs from device", e);
                    ctx.fail(500, e);
                }) //
                .onSuccess(ctx::json) //
        );

        // Add pack from library to device
        router.post("/addFromLibrary").blockingHandler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            String packPath = ctx.getBodyAsJson().getString("path");
            Path packFile = LibraryService.libraryPath().resolve(packPath);
            // Start transfer to device
            Future.fromCompletionStage(storyTellerService.addPack(uuid, packFile)) //
                    .onFailure(e -> {
                        LOGGER.error("Failed to transfer pack to device", e);
                        ctx.fail(500, e);
                    }) //
                    .onSuccess(optTransferId -> optTransferId.ifPresentOrElse(
                            // Return the transfer id, which is used to monitor transfer progress
                            transferId -> ctx.json(new JsonObject().put("transferId", transferId)), //
                            () -> {
                                LOGGER.error("Failed to transfer pack to device");
                                ctx.fail(500);
                            }) //
            );
        });

        // Remove pack from device
        router.post("/removeFromDevice").handler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            Future.fromCompletionStage(storyTellerService.deletePack(uuid)) //
                    .onFailure(e -> {
                        LOGGER.error("Failed to remove pack from device", e);
                        ctx.fail(500, e);
                    }) //
                    .onSuccess(removed -> {
                        if (Boolean.TRUE.equals(removed)) {
                            ctx.json(new JsonObject().put("success", true));
                        } else {
                            LOGGER.error("Pack was not removed from device");
                            ctx.fail(500);
                        }
                    });
        });

        // Reorder packs on device
        router.post("/reorder").handler(ctx -> {
            @SuppressWarnings("unchecked")
            List<String> uuids = ctx.getBodyAsJson().getJsonArray("uuids").getList();
            Future.fromCompletionStage(storyTellerService.reorderPacks(uuids)) //
                    .onFailure(e -> {
                        LOGGER.error("Failed to reorder packs on device", e);
                        ctx.fail(500, e);
                    }) //
                    .onSuccess(reordered -> {
                        if (Boolean.TRUE.equals(reordered)) {
                            ctx.json(new JsonObject().put("success", true));
                        } else {
                            LOGGER.error("Failed to reorder packs on device");
                            ctx.fail(500);
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
            Future.fromCompletionStage(storyTellerService.extractPack(uuid, path)) //
                    .onFailure(e -> {
                        LOGGER.error("Failed to transfer pack from device", e);
                        ctx.fail(500, e);
                    }) //
                    .onSuccess(optTransferId -> 
                        optTransferId.ifPresentOrElse(
                                // Return the transfer id, which is used to monitor transfer progress
                                transferId -> ctx.json(new JsonObject().put("transferId", transferId)), //
                                () -> {
                                    LOGGER.error("Failed to transfer pack from device");
                                    ctx.fail(500);
                                })
                    );
        });

        // Dump important sectors
        router.post("/dump").handler(ctx -> {
            Path outputPath = Path.of(ctx.getBodyAsJson().getString("outputPath"));
            // Dump important sector into outputPath
            Future.fromCompletionStage(storyTellerService.dump(outputPath)) //
                    .onFailure(e -> {
                        LOGGER.error("Failed to dump important sectors from device", e);
                        ctx.fail(500, e);
                    }) //
                    .onSuccess(s -> ctx.json(new JsonObject().put("success", true)));
        });

        return router;
    }
}
