/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import studio.core.v1.Constants;
import studio.webui.service.LibraryService;

public class LibraryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryController.class);

    private static final ScheduledThreadPoolExecutor THREAD_POOL = new ScheduledThreadPoolExecutor(2);

    public static Router apiRouter(Vertx vertx, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Local library device metadata
        router.get("/infos").blockingHandler(ctx -> {
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                    .end(Json.encode(libraryService.libraryInfos()));
        });

        // Local library packs list
        router.get("/packs").blockingHandler(ctx -> {
            JsonArray libraryPacks = libraryService.packs();
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                    .end(Json.encode(libraryPacks));
        });

        // Local library pack download
        router.post("/download").blockingHandler(ctx -> {
            String packPath = ctx.getBodyAsJson().getString("path");
            libraryService.getRawPackFile(packPath)
                    .ifPresentOrElse(
                            path -> ctx.response()
                                    .putHeader("Content-Length", "" + path.toFile().length())
                                    .sendFile(path.toAbsolutePath().toString()),
                            () -> {
                                LOGGER.error("Failed to download pack from library");
                                ctx.fail(500);
                            }
                    );
        });

        // Local library pack upload
        router.post("/upload").blockingHandler(ctx -> {
            String packPath = ctx.request().getFormAttribute("path");
            boolean added = libraryService.addPackFile(packPath, ctx.fileUploads().iterator().next().uploadedFileName());
            if (added) {
                ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                        .end(Json.encode(new JsonObject().put("success", true)));
            } else {
                LOGGER.error("Pack was not added to library");
                ctx.fail(500);
            }
        });

        // Local library pack conversion
        router.post("/convert").blockingHandler(ctx -> {
            String packPath = ctx.getBodyAsJson().getString("path");
            Boolean allowEnriched = ctx.getBodyAsJson().getBoolean("allowEnriched", false);
            String format = ctx.getBodyAsJson().getString("format");
            // Perform conversion/uncompression asynchronously
            Promise<Path> promisedPack = Promise.promise();
            THREAD_POOL.schedule(() -> {
                Optional<Path> optPath = null;
                if (Constants.PACK_FORMAT_RAW.equalsIgnoreCase(format)) {
                    optPath = libraryService.addConvertedRawPackFile(packPath, allowEnriched);
                } else if (Constants.PACK_FORMAT_FS.equalsIgnoreCase(format)) {
                    optPath = libraryService.addConvertedFsPackFile(packPath, allowEnriched);
                } else if (Constants.PACK_FORMAT_ARCHIVE.equalsIgnoreCase(format)) {
                    optPath = libraryService.addConvertedArchivePackFile(packPath);
                } else {
                    ctx.fail(400);
                    return;
                }
                optPath.ifPresentOrElse(p -> promisedPack.tryComplete(p),
                        () -> promisedPack.tryFail("Failed to read or convert pack to " + format + " format"));
                promisedPack.tryComplete();
            }, 1, TimeUnit.SECONDS);
            
            promisedPack.future().onComplete(maybeConvertedPack -> {
                if (maybeConvertedPack.succeeded()) {
                    // Return path to converted file within library
                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                            .end(Json.encode(new JsonObject()
                                    .put("success", true)
                                    .put("path", maybeConvertedPack.result().toString())
                            ));
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
                        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.MIME_JSON)
                        .end(Json.encode(new JsonObject().put("success", true)));
            } else {
                LOGGER.error("Pack was not removed from library");
                ctx.fail(500);
            }
        });

        return router;
    }
}
