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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import studio.core.v1.utils.PackFormat;
import studio.webui.service.LibraryService;

public class LibraryController {

    private static final Logger LOGGER = LogManager.getLogger(LibraryController.class);

    private static final ScheduledThreadPoolExecutor THREAD_POOL = new ScheduledThreadPoolExecutor(2);

    private LibraryController() {
        throw new IllegalArgumentException("Utility class");
    }

    public static Router apiRouter(Vertx vertx, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Local library device metadata
        router.get("/infos").handler(ctx -> {
            ctx.json(libraryService.libraryInfos());
        });

        // Local library packs list
        router.get("/packs").blockingHandler(ctx -> {
            long t1 = System.currentTimeMillis();
            JsonArray libraryPacks = libraryService.packs();
            long t2 = System.currentTimeMillis();
            LOGGER.info("Library packs scanned in {}ms", t2-t1);
            ctx.json(libraryPacks);
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
                ctx.json(new JsonObject().put("success", true));
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
                PackFormat packFormat;
                try {
                    packFormat = PackFormat.valueOf(format.toUpperCase());
                }catch(IllegalArgumentException e ) {
                    LOGGER.error("Invalid PackFormat : {}", format);
                    ctx.fail(400);
                    return;
                }
                Optional<Path> optPath = Optional.empty();
                if (PackFormat.RAW == packFormat) {
                    optPath = libraryService.addConvertedRawPackFile(packPath, allowEnriched);
                } else if (PackFormat.FS == packFormat) {
                    optPath = libraryService.addConvertedFsPackFile(packPath);
                } else if (PackFormat.ARCHIVE == packFormat) {
                    optPath = libraryService.addConvertedArchivePackFile(packPath);
                }
                optPath.ifPresentOrElse(promisedPack::tryComplete,
                        () -> promisedPack.tryFail("Failed to read or convert pack to " + format + " format"));
                promisedPack.tryComplete();
            }, 0, TimeUnit.SECONDS);

            promisedPack.future().onComplete(maybeConvertedPack -> {
                if (maybeConvertedPack.succeeded()) {
                    // Return path to converted file within library
                    ctx.json(new JsonObject().put("success", true).put("path", maybeConvertedPack.result().toString()));
                } else {
                    LOGGER.error("Failed to read or convert pack");
                    ctx.fail(500, maybeConvertedPack.cause());
                }
            });
        });

        // Remove pack from device
        router.post("/remove").handler(ctx -> {
            String packPath = ctx.getBodyAsJson().getString("path");
            boolean removed = libraryService.deletePack(packPath);
            if (removed) {
                ctx.json(new JsonObject().put("success", true));
            } else {
                LOGGER.error("Pack was not removed from library");
                ctx.fail(500);
            }
        });

        return router;
    }
}
