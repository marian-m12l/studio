/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui;

import java.awt.Desktop;
import java.net.URI;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.vertx.mutiny.ext.web.Router;
import studio.metadata.DatabaseMetadataService;

@QuarkusMain
public class Studio {

    private static final Logger LOGGER = LogManager.getLogger(Studio.class);

    @Produces
    DatabaseMetadataService databaseMetadataService = new DatabaseMetadataService();

    public static void main(String... args) {
        LOGGER.info("Running Studio");
        Quarkus.run(args);
    }

    @ApplicationScoped
    static class MainVerticle { // extends AbstractVerticle {

//      public static final String MIME_JSON = "application/json";

//    private LibraryService libraryService;
//    private IStoryTellerService storyTellerService;

        @ConfigProperty(name = "studio.host")
        String host;

        @ConfigProperty(name = "studio.port")
        String port;

        @ConfigProperty(name = "studio.open.browser", defaultValue = "false")
        boolean openBrowser;

        public void init(@Observes Router router) {
            // Service that manages pack metadata
//        DatabaseMetadataService databaseMetadataService = new DatabaseMetadataService();

            // Service that manages local library
//        libraryService = new LibraryService(databaseMetadataService);

            // Service that manages updates
//        evergreenService = new EvergreenService(vertx);

            // Service that manages link with the story teller device
//        if (isDevMode()) {
//            LOGGER.warn("[DEV MODE] Initializing mock storyteller service");
//            storyTellerService = new MockStoryTellerService(vertx.eventBus(), databaseMetadataService);
//        } else {
//            storyTellerService = new StoryTellerService(vertx.eventBus(), databaseMetadataService);
//        }

            // Config
            // String host = StudioConfig.STUDIO_HOST.getValue();
            // int port = Integer.parseInt(StudioConfig.STUDIO_PORT.getValue());

            // Handle cross-origin calls
//        router.route().handler(CorsHandler.create("http://" + host + ":.*") //
//                .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST)) //
//                .allowedHeaders(Set.of(HttpHeaders.ACCEPT.toString(), HttpHeaders.CONTENT_TYPE.toString(),
//                        HttpHeaderNames.X_REQUESTED_WITH.toString())) //
//                .exposedHeaders(Set.of(HttpHeaders.CONTENT_LENGTH.toString(), HttpHeaders.CONTENT_TYPE.toString())) //
//        );

            // Bridge event-bus to client-side app
//        router.mountSubRouter("/eventbus", eventBusHandler(vertx));

            // Rest API
//        router.mountSubRouter("/api", apiRouter(router));

            // Static resources (/webroot)
//        router.route().handler(StaticHandler.create().setCachingEnabled(false));

            // Error handler
//        ErrorHandler errorHandler = ErrorHandler.create(vertx, true);
//        router.route().failureHandler(ctx -> {
//            Throwable failure = ctx.failure();
//            LOGGER.error("Exception thrown", failure);
//            errorHandler.handle(ctx);
//        });

            // Start HTTP server
            // vertx.createHttpServer().requestHandler(router).listen(port);

            // Automatically open URL in browser, unless instructed otherwise
//        String openBrowser = StudioConfig.STUDIO_OPEN_BROWSER.getValue();
            // if (Boolean.parseBoolean(openBrowser)) {
            if (openBrowser) {
                LOGGER.info("Opening URL in default browser...");
                if (Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().browse(new URI("http://" + host + ":" + port));
                    } catch (Exception e) {
                        LOGGER.error("Failed to open URL in default browser", e);
                    }
                }
            }
        }

//    private SockJSHandler eventBusHandler(Vertx vertx) {
//        LOGGER.info("Init sockjs EventBus");
//        PermittedOptions address = new PermittedOptions().setAddressRegex("storyteller\\..+");
//        SockJSBridgeOptions options = new SockJSBridgeOptions().addOutboundPermitted(address);
//        SockJSHandler sock = SockJSHandler.create(vertx);
//        sock.bridge(options, event -> {
//            if (event.type() == BridgeEventType.SOCKET_CREATED) {
//                LOGGER.info("New sockjs client");
//            }
//            event.complete(true);
//        });
//        return sock;
//    }

//    private Router apiRouter(Router router) {
//        Router router = Router.router(vertx);

        // Handle JSON
//        router.route().handler(BodyHandler.create()).consumes(MIME_JSON).produces(MIME_JSON);

        // Device services
//        router.mountSubRouter("/device", DeviceController.apiRouter(router, storyTellerService));

        // Library services
//        router.mountSubRouter("/library", LibraryController.apiRouter(router, libraryService));

        // Evergreen services
        // router.mountSubRouter("/evergreen", EvergreenController.apiRouter(router,
        // evergreenService));

//        return router;
//    }

//    private boolean isDevMode() {
//        return "dev".equals(ProfileManager.getActiveProfile());
//    }
    }
}