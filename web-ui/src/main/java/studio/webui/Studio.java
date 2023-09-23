/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QuarkusMain
public class Studio {

    private static final Logger LOGGER = LoggerFactory.getLogger(Studio.class);

    public static void main(String... args) {
        LOGGER.info("Running Studio");
        Quarkus.run(args);
    }

    @ApplicationScoped
    static class MainVerticle {

        @ConfigProperty(name = "studio.host")
        String host;

        @ConfigProperty(name = "studio.port")
        Integer port;

        @ConfigProperty(name = "studio.open.browser", defaultValue = "false")
        boolean openBrowser;

        void init(@Observes StartupEvent se) {
            // Automatically open URL in browser, unless instructed otherwise
            if (!openBrowser) {
                return;
            }
            String os = System.getProperty("os.name");
            ProcessBuilder pb = new ProcessBuilder();
            try {
                String url = new URI("http", null, host, port, "/", null, null).toString();
                LOGGER.info("Opening URL {} in default browser...", url);
                if (os.startsWith("Mac OS")) {
                    pb.command("open", url);
                } else if (os.startsWith("Windows")) {
                    pb.command("rundll32", "url.dll,FileProtocolHandler", url);
                } else { // unix or linux
                    pb.command("xdg-open", url);
                }
                pb.start();
            } catch (IOException | URISyntaxException e) {
                LOGGER.error("Failed to open URL in default browser", e);
            }
        }
    }
}
