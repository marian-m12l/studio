/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import studio.webui.service.WatchdogService;

public class WatchdogController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchdogController.class);
    
    public static Router apiRouter(Vertx vertx, WatchdogService watchdogService) {
        Router router = Router.router(vertx);

        // Supported versions
        router.get("/supported").blockingHandler(ctx -> {
            watchdogService.supported().setHandler(maybeJson -> {
                if (maybeJson.succeeded()) {
                    ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(maybeJson.result()));
                } else {
                    LOGGER.error("Failed to get current version infos");
                    ctx.fail(500, maybeJson.cause());
                }
            });
        });

        // Latest versions
        router.get("/latest").blockingHandler(ctx -> {
            watchdogService.latest().setHandler(maybeJson -> {
                if (maybeJson.succeeded()) {
                    ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(maybeJson.result()));
                } else {
                    LOGGER.error("Failed to get latest release");
                    ctx.fail(500, maybeJson.cause());
                }
            });
        });

        return router;
    }
}
