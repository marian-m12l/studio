/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;

public class EvergreenService {

    public static final String GITHUB_API_FQDN = "api.github.com";
    public static final String GITHUB_API_ROOT = "/repos/marian-m12l/studio";
    public static final String GITHUB_API_LATEST_RELEASE = GITHUB_API_ROOT + "/releases/latest";

    private final Logger LOGGER = LoggerFactory.getLogger(EvergreenService.class);

    private ConfigRetriever configRetriever;
    private WebClient webClient;

    public EvergreenService(Vertx vertx) {
        ConfigStoreOptions propertiesStore = new ConfigStoreOptions()
                .setType("file")
                .setFormat("properties")
                .setConfig(new JsonObject().put("path", "evergreen.properties"));
        ConfigRetrieverOptions options = new ConfigRetrieverOptions().addStore(propertiesStore);
        configRetriever = ConfigRetriever.create(vertx, options);
        webClient = WebClient.create(vertx);
    }

    public Future<JsonObject> infos() {
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
        // Latest available release (from github)
        Future<JsonObject> future = Future.future();
        webClient
                .get(443, GITHUB_API_FQDN, GITHUB_API_LATEST_RELEASE)
                .ssl(true)
                .send(ar -> {
                    if (ar.succeeded()) {
                        future.tryComplete(ar.result().bodyAsJsonObject());
                    } else {
                        future.tryFail(ar.cause());
                    }
                });
        return future;
    }

}
