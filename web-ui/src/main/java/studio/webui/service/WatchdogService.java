/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;

import java.net.MalformedURLException;
import java.net.URL;

public class WatchdogService {

    public static final String LUNII_LAUNCHER_FQDN = "server.lunii.com";
    public static final String LUNII_LAUNCHER_LATEST_PATH = "/launcher";
    public static final String LUNII_DATABASE_FQDN = "server.lunii.fr";
    public static final String LUNII_DATABASE_URL_PATH = "/databaseUrl";
    public static final String LUNII_APP_VERSION_PATH = "/version.json";
    public static final String LUNII_MANIFEST_FQDN = "storage.lunii.fr";
    public static final String LUNII_MANIFEST_JSON_PATH = "/public/deploy/last/app.json";

    private final Logger LOGGER = LoggerFactory.getLogger(WatchdogService.class);

    private ConfigRetriever configRetriever;
    private WebClient webClient;

    public WatchdogService(Vertx vertx) {
        ConfigStoreOptions propertiesStore = new ConfigStoreOptions()
                .setType("file")
                .setFormat("properties")
                .setConfig(new JsonObject().put("path", "watchdog.properties"));
        ConfigRetrieverOptions options = new ConfigRetrieverOptions().addStore(propertiesStore);
        configRetriever = ConfigRetriever.create(vertx, options);
        webClient = WebClient.create(vertx);
    }

    public Future<JsonObject> supported() {
        Future<JsonObject> future = Future.future();
        configRetriever.getConfig(ar -> {
            if (ar.succeeded()) {
                future.tryComplete(ar.result());
            } else {
                future.tryFail(ar.cause());
            }
        });
        return future;
    }

    public Future<JsonObject> latest() {
        // Latest available launcher (from Lunii servers)
        Future<String> launcherChecksumFuture = Future.future();
        webClient
                .get(443, LUNII_LAUNCHER_FQDN, LUNII_LAUNCHER_LATEST_PATH)
                .ssl(true)
                .send(ar -> {
                    if (ar.succeeded()) {
                        String response = ar.result().bodyAsString();
                        launcherChecksumFuture.tryComplete(response.substring(0, response.indexOf(';')));
                    } else {
                        launcherChecksumFuture.tryFail(ar.cause());
                    }
                });
        // Latest available app version (from Lunii servers)
        Future<JsonObject> appVersionFuture = Future.future();
        webClient
                .get(443, LUNII_DATABASE_FQDN, LUNII_DATABASE_URL_PATH)
                .ssl(true)
                .send(ar -> {
                    if (ar.succeeded()) {
                        String response = ar.result().bodyAsString();
                        try {
                            URL databaseUrl = new URL(response);
                            int port = databaseUrl.getPort() == -1 ? databaseUrl.getDefaultPort() : databaseUrl.getPort();
                            String host = databaseUrl.getHost();
                            boolean ssl = "https".equalsIgnoreCase(databaseUrl.getProtocol());
                            webClient
                                    .get(port, host, LUNII_APP_VERSION_PATH)
                                    .ssl(ssl)
                                    .send(ar2 -> {
                                        if (ar2.succeeded()) {
                                            appVersionFuture.tryComplete(ar2.result().bodyAsJsonObject());
                                        } else {
                                            appVersionFuture.tryFail(ar2.cause());
                                        }
                                    });
                        } catch (MalformedURLException e) {
                            appVersionFuture.tryFail(e);
                        }
                    } else {
                        appVersionFuture.tryFail(ar.cause());
                    }
                });
        // Latest available manifest (from Lunii servers)
        Future<Long> launcherManifestTimestampFuture = Future.future();
        webClient
                .get(80, LUNII_MANIFEST_FQDN, LUNII_MANIFEST_JSON_PATH)
                .ssl(false)
                .send(ar -> {
                    if (ar.succeeded()) {
                        JsonObject response = ar.result().bodyAsJsonObject();
                        launcherManifestTimestampFuture.tryComplete(response.getLong("ts"));
                    } else {
                        launcherManifestTimestampFuture.tryFail(ar.cause());
                    }
                });
        return CompositeFuture.all(launcherChecksumFuture, appVersionFuture, launcherManifestTimestampFuture)
                .map(results -> new JsonObject()
                        .put("launcherChecksum", results.list().get(0))
                        .put("appVersionMajor", ((JsonObject)results.list().get(1)).getInteger("major"))
                        .put("appVersionMinor", ((JsonObject)results.list().get(1)).getInteger("minor"))
                        .put("manifestTimestamp", results.list().get(2))
                );
    }

}
