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
import io.vertx.ext.web.Router;
import studio.webui.service.LibraryService;

import java.util.Optional;

public class LibraryController {
    
    public static Router apiRouter(Vertx vertx, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Local library device metadata
        router.get("/infos").handler(ctx -> {
            Optional<JsonObject> maybeLibraryInfos = libraryService.libraryInfos();
            maybeLibraryInfos.ifPresentOrElse(
                    libraryInfos -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(libraryInfos)),
                    () -> ctx.fail(404)
            );
        });

        // Local library packs list
        router.get("/packs").handler(ctx -> {
            JsonArray libraryPacks = libraryService.packs();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(libraryPacks));
        });

        return router;
    }
}
