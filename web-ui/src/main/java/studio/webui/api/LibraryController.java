/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;


import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import studio.core.v1.utils.PackFormat;
import studio.core.v1.utils.exception.StoryTellerException;
import studio.webui.service.LibraryService;

public class LibraryController {

    private static final Logger LOGGER = LogManager.getLogger(LibraryController.class);

    private LibraryController() {
        throw new IllegalArgumentException("Utility class");
    }

    public static Router apiRouter(Vertx vertx, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Local library device metadata
        router.get("/infos").handler(ctx -> ctx.json(libraryService.libraryInfos()));

        // Local library packs list
        router.get("/packs").blockingHandler(ctx -> {
            long t1 = System.currentTimeMillis();
            JsonArray libraryPacks = libraryService.packs();
            long t2 = System.currentTimeMillis();
            LOGGER.info("Library packs scanned in {}ms", t2-t1);
            ctx.json(libraryPacks);
        });

        // Local library pack download
        router.post("/download").handler(ctx -> {
            String packPath = ctx.getBodyAsJson().getString("path");
            LOGGER.info("Download {}", packPath);
            ctx.response().sendFile(libraryService.getPackFile(packPath).toString());
        });

        // Local library pack upload
        router.post("/upload").handler(BodyHandler.create() //
                .setMergeFormAttributes(true) //
                .setUploadsDirectory(LibraryService.tmpDirPath().toString()));

        router.post("/upload").handler(ctx -> {
            String packPath = ctx.request().getFormAttribute("path");
            LOGGER.info("Upload {}", packPath);
            boolean added = false;
            Iterator<FileUpload> it = ctx.fileUploads().iterator();
            if(it.hasNext()) {
                added = libraryService.addPackFile(packPath, it.next().uploadedFileName());
            }
            if (added) {
                ctx.json(new JsonObject().put("success", true));
            } else {
                LOGGER.error("Pack {} was not added to library", packPath);
                ctx.fail(500);
            }
        });

        // Local library pack conversion
        router.post("/convert").handler(ctx -> {
            JsonObject body = ctx.getBodyAsJson();
            String packPath = body.getString("path");
            Boolean allowEnriched = body.getBoolean("allowEnriched", false);
            String format = body.getString("format");

            // Perform conversion/uncompression asynchronously
            WorkerExecutor executor = vertx.createSharedWorkerExecutor("pack-converter", 1, 20, TimeUnit.MINUTES);
            executor.executeBlocking( //
                    future -> {
                        try {
                            PackFormat packFormat = PackFormat.valueOf(format.toUpperCase());
                            Path newPackPath = libraryService.addConvertedPack(packPath, packFormat, allowEnriched);
                            future.complete(newPackPath);
                        } catch (IllegalArgumentException | StoryTellerException e) {
                            future.fail(e);
                        }
                    }, //
                    res -> {
                        if (res.succeeded()) {
                            // Return path to converted file within library
                            ctx.json(new JsonObject().put("success", true).put("path", res.result().toString()));
                        } else {
                            LOGGER.error("Failed to read or convert pack");
                            ctx.fail(500, res.cause());
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
