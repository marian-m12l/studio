/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import studio.metadata.DatabaseMetadataService;
import studio.webui.api.DeviceController;
import studio.webui.api.EvergreenController;
import studio.webui.api.LibraryController;
import studio.webui.service.*;
import studio.webui.service.mock.MockStoryTellerService;

import java.util.Set;

public class MainVerticle extends AbstractVerticle {

    private final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

    private DatabaseMetadataService databaseMetadataService;
    private LibraryService libraryService;
    private EvergreenService evergreenService;
    private IStoryTellerService storyTellerService;

    @Override
    public void start() {

        // Service that manages pack metadata
        databaseMetadataService = new DatabaseMetadataService(false);

        // Service that manages local library
        libraryService = new LibraryService(databaseMetadataService);

        // Service that manages updates
        evergreenService = new EvergreenService(vertx);

        // Service that manages link with the story teller device
        if (isDevMode()) {
            LOGGER.warn("[DEV MODE] Initializing mock storyteller service");
            storyTellerService = new MockStoryTellerService(vertx.eventBus(), databaseMetadataService);
        } else {
            storyTellerService = new StoryTellerService(vertx.eventBus(), databaseMetadataService);
        }


        Router router = Router.router(vertx);

        // Handle cross-origin calls
        router.route().handler(CorsHandler.create("http://localhost:.*")
                .allowedMethods(Set.of(
                        HttpMethod.GET,
                        HttpMethod.POST
                ))
                .allowedHeaders(Set.of(
                        HttpHeaders.ACCEPT.toString(),
                        HttpHeaders.CONTENT_TYPE.toString(),
                        "x-requested-with"
                ))
                .exposedHeaders(Set.of(
                        HttpHeaders.CONTENT_LENGTH.toString(),
                        HttpHeaders.CONTENT_TYPE.toString()
                ))
        );

        // Bridge event-bus to client-side app
        router.route("/eventbus/*").handler(eventBusHandler());

        // Rest API
        router.mountSubRouter("/api", apiRouter());

        // Static resources (/webroot)
        router.route().handler(StaticHandler.create().setCachingEnabled(false));

        // Error handler
        ErrorHandler errorHandler = ErrorHandler.create(true);
        router.route().failureHandler(ctx -> {
            Throwable failure = ctx.failure();
            LOGGER.error("Exception thrown", failure);
            errorHandler.handle(ctx);
        });

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

    private Router apiRouter() {
        Router router = Router.router(vertx);

        // Handle JSON
        router.route().handler(BodyHandler.create());
        router.route().consumes("application/json");
        router.route().produces("application/json");


        // Device services
        router.mountSubRouter("/device", DeviceController.apiRouter(vertx, storyTellerService, libraryService));

        // Library services
        router.mountSubRouter("/library", LibraryController.apiRouter(vertx, libraryService));

        // Evergreen services
        router.mountSubRouter("/evergreen", EvergreenController.apiRouter(vertx, evergreenService));

        return router;
    }

    private boolean isDevMode() {
        return "dev".equalsIgnoreCase(System.getProperty("env", "prod"));
    }
}
