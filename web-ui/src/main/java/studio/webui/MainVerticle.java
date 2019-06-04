/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import studio.webui.service.StoryTellerService;

import java.util.Optional;
import java.util.Set;

public class MainVerticle extends AbstractVerticle {

    private final Logger LOGGER = LoggerFactory.getLogger(StoryTellerService.class);

    private StoryTellerService storyTellerService;

    @Override
    public void start() {

        // Service that manages link with the story teller device
        storyTellerService = new StoryTellerService(vertx.eventBus());


        Router router = Router.router(vertx);

        // Bridge event-bus to client-side app
        router.route("/eventbus/*").handler(eventBusHandler());

        // Rest API
        router.mountSubRouter("/api", apiRouter(storyTellerService));

        // Static resources (/webroot)
        router.route().handler(StaticHandler.create().setCachingEnabled(false));

        vertx.createHttpServer().requestHandler(router).listen(8080);
    }

    private SockJSHandler eventBusHandler() {
        BridgeOptions options = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions().setAddressRegex("storyteller\\.(.+)"));
        return SockJSHandler.create(vertx).bridge(options, event -> {
            if (event.type() == BridgeEventType.SOCKET_CREATED) {
                LOGGER.debug("New sockjs client");
            }
            event.complete(true);
        });
    }

    private Router apiRouter(StoryTellerService storyTellerService) {
        Router router = Router.router(vertx);

        // Handle cross-origin calls
        router.route().handler(CorsHandler.create("*")
                .allowedMethods(Set.of(
                        HttpMethod.GET,
                        HttpMethod.POST
                ))
                .allowedHeaders(Set.of(
                        HttpHeaders.ACCEPT.toString(),
                        HttpHeaders.CONTENT_TYPE.toString()
                ))
        );

        // Handle JSON
        router.route().handler(BodyHandler.create());
        router.route().consumes("application/json");
        router.route().produces("application/json");

        // Plugged device metadata
        router.get("/device/infos").handler(ctx -> {
            Optional<JsonObject> maybeDeviceInfos = storyTellerService.deviceInfos();
            maybeDeviceInfos.ifPresentOrElse(
                    deviceInfos -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(deviceInfos)),
                    () -> ctx.response()
                            .setStatusCode(404)
            );
        });
        // Plugged device packs list
        router.get("/device/packs").handler(ctx -> {
            JsonArray devicePacks = storyTellerService.packs();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(devicePacks));
        });

        return router;
    }
}
