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
import studio.webui.service.LibraryService;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class LibraryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryController.class);
    
    public static Router apiRouter(Vertx vertx, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Local library device metadata
        router.get("/infos").blockingHandler(ctx -> {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(libraryService.libraryInfos()));
        });

        // Local library packs list
        router.get("/packs").blockingHandler(ctx -> {
            JsonArray libraryPacks = libraryService.packs();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(libraryPacks));
        });

        // Local library pack download
        router.post("/download").blockingHandler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            String packPath = ctx.getBodyAsJson().getString("path");
            libraryService.getRawPackFile(packPath)
                    .ifPresentOrElse(
                            file -> ctx.response()
                                    .putHeader("Content-Length", "" + file.length())
                                    .sendFile(file.getAbsolutePath()),
                            () -> {
                                LOGGER.error("Failed to download pack from library");
                                ctx.fail(500);
                            }
                    );
        });

        // Local library pack upload
        router.post("/upload").blockingHandler(ctx -> {
            String uuid = ctx.request().getFormAttribute("uuid");
            String packPath = ctx.request().getFormAttribute("path");
            boolean added = libraryService.addPackFile(packPath, ctx.fileUploads().iterator().next().uploadedFileName());
            if (added) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject().put("success", true)));
            } else {
                LOGGER.error("Pack was not added to library");
                ctx.fail(500);
            }
        });

        // Local library pack convert to archive
        router.post("/convert").blockingHandler(ctx -> {
            String uuid = ctx.getBodyAsJson().getString("uuid");
            String packPath = ctx.getBodyAsJson().getString("path");
            // First, get the pack file, potentially converted from binary format to archive format
            // Perform conversion/compression asynchronously
            Future<File> futureConvertedPack = Future.future();
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    libraryService.getArchivePackFile(packPath)
                            .ifPresentOrElse(
                                    packFile -> futureConvertedPack.tryComplete(packFile),
                                    () -> futureConvertedPack.tryFail("Failed to read or convert pack"));
                    futureConvertedPack.tryComplete();
                }
            }, 1000);

            futureConvertedPack.setHandler(maybeConvertedPack -> {
                if (maybeConvertedPack.succeeded()) {
                    // Then, add converted pack to library
                    boolean added = libraryService.addPackFile(packPath + ".zip", maybeConvertedPack.result().getAbsolutePath());
                    if (added) {
                        ctx.response()
                                .putHeader("content-type", "application/json")
                                .end(Json.encode(new JsonObject().put("success", true)));
                    } else {
                        LOGGER.error("Converted pack was not added to library");
                        ctx.fail(500);
                    }
                } else {
                    LOGGER.error("Failed to read or convert pack");
                    ctx.fail(500, maybeConvertedPack.cause());
                }
            });
        });

        // Remove pack from device
        router.post("/remove").blockingHandler(ctx -> {
            String packPath = ctx.getBodyAsJson().getString("path");
            boolean removed = libraryService.deletePack(packPath);
            if (removed) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject().put("success", true)));
            } else {
                LOGGER.error("Pack was not removed from library");
                ctx.fail(500);
            }
        });

        return router;
    }
}
