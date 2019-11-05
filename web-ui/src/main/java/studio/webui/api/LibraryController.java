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

public class LibraryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryController.class);
    
    public static Router apiRouter(Vertx vertx, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Local library device metadata
        router.get("/infos").handler(ctx -> {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(libraryService.libraryInfos()));
        });

        // Local library packs list
        router.get("/packs").handler(ctx -> {
            JsonArray libraryPacks = libraryService.packs();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(libraryPacks));
        });

        // Local library pack download
        router.post("/download").handler(ctx -> {
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
        router.post("/upload").handler(ctx -> {
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

        return router;
    }
}
