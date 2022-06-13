package studio.webui.api;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.handler.sockjs.SockJSHandler;

@ApplicationScoped
public class SockJsEventBusBridge {

    private static final Logger LOGGER = LogManager.getLogger(SockJsEventBusBridge.class);

    public void init(@Observes Router router, Vertx vertx) {
        LOGGER.info("Start Sockjs EventBus");
        PermittedOptions address = new PermittedOptions().setAddressRegex("storyteller\\..+");
        SockJSBridgeOptions options = new SockJSBridgeOptions().addOutboundPermitted(address);

        SockJSHandler sockHandler = SockJSHandler.create(vertx);
        sockHandler.bridge(options, event -> {
            switch (event.type()) {
            case SOCKET_CREATED:
                LOGGER.info("Sockjs open");
                break;
            case SOCKET_CLOSED:
                LOGGER.info("Sockjs closed");
                break;
            default:
                break;
            }
            event.complete(true);
        });
        // add as route
        router.route("/eventbus/*").handler(sockHandler);
    }
}
