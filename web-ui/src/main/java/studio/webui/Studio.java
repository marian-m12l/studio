/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Studio {

    private static final Logger LOGGER = LogManager.getLogger(Studio.class);

    public static void main(String... args) {
        LOGGER.info("Running Studio");
        Quarkus.run(args);
    }

    @ApplicationScoped
    static class MainVerticle {

        @ConfigProperty(name = "studio.host")
        String host;

        @ConfigProperty(name = "studio.port")
        String port;

        @ConfigProperty(name = "studio.open.browser", defaultValue = "false")
        boolean openBrowser;

        void init(@Observes StartupEvent se) {
            // Automatically open URL in browser, unless instructed otherwise
            if (openBrowser && Desktop.isDesktopSupported()) {
                LOGGER.info("Opening URL in default browser...");
                try {
                    Desktop.getDesktop().browse(new URI("http://" + host + ":" + port));
                } catch (IOException | URISyntaxException e) {
                    LOGGER.error("Failed to open URL in default browser", e);
                }
            }
        }
    }
}