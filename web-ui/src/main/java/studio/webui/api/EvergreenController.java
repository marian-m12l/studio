/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import studio.webui.service.EvergreenService;

public class EvergreenController {

    private static final Logger LOGGER = LogManager.getLogger(EvergreenController.class);

    private EvergreenController() {
        throw new IllegalArgumentException("Utility class");
    }

    public static Router apiRouter(Vertx vertx, EvergreenService evergreenService) {
        Router router = Router.router(vertx);

        // Current version
        router.get("/infos").handler(ctx -> evergreenService.infos().onComplete(maybeJson -> {
            if (maybeJson.succeeded()) {
                LOGGER.info("Current version infos: {}", maybeJson.result());
                ctx.json(maybeJson.result());
            } else {
                LOGGER.error("Failed to get current version infos");
                ctx.fail(500, maybeJson.cause());
            }
        }));

        // Latest release
        router.get("/latest").blockingHandler(ctx -> evergreenService.latest().onComplete(maybeJson -> {
            if (maybeJson.succeeded()) {
                ctx.json(maybeJson.result());
            } else {
                LOGGER.error("Failed to get latest release");
                ctx.fail(500, maybeJson.cause());
            }
        }));

        // Announce
        router.get("/announce").blockingHandler(ctx -> evergreenService.announce().onComplete(maybeJson -> {
            if (maybeJson.succeeded()) {
                ctx.json(maybeJson.result());
            } else {
                LOGGER.error("Failed to get announce");
                ctx.fail(500, maybeJson.cause());
            }
        }));

        return router;
    }
}
