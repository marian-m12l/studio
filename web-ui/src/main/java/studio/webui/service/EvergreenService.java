/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class EvergreenService {

    private static final Logger LOGGER = LogManager.getLogger(EvergreenService.class);

    public static final String GITHUB_API_FQDN = "api.github.com";
    public static final String GITHUB_RAW_FQDN = "raw.githubusercontent.com";
    public static final String GITHUB_API_ROOT = "/repos/marian-m12l/studio";
    public static final String GITHUB_API_LATEST_RELEASE = GITHUB_API_ROOT + "/releases/latest";
    public static final String GITHUB_API_ANNOUNCE_COMMIT = GITHUB_API_ROOT + "/commits?path=ANNOUNCE.md";
    public static final String GITHUB_API_ANNOUNCE_CONTENT = "/marian-m12l/studio/master/ANNOUNCE.md";

    public static final String GITHUB_API_BUILTIN_ANNOUNCE_CONTENT_EN = "### Good news, everyone!\n\nSTUdio is improving, at a slow but steady pace, and that's primarily thanks to your feedback. Thanks for letting me know what's missing or broken (and also what's not \uD83D\uDE01)!\n\nTo better benefit from this feedback, I wanted a way to communicate directly to you and let you know how STUdio is evolving. So here it is: **a brand new announce mechanism!**\n\nI plan to use it sparsely, to announce major features and occasionally request your feedback (a beta version will be released soon). These announces **are only displayed once**, and **you can opt-out if you feel like it**.\n\nHere's to all the great story packs you're building! \uD83C\uDF7B";
    public static final String GITHUB_API_BUILTIN_ANNOUNCE_CONTENT_FR = "### Good news, everyone!\n\nSTUdio s'améliore, lentement mais sûrement, et le mérite en revient grandement à tous vos retours. Merci de me faire savoir ce qu'il manque ou ce qui est cassé (et aussi ce qui ne l'est pas \uD83D\uDE01) !\n\nPour tirer avantage au mieux de vos retours, je souhaitais pouvoir communiquer directement avec vous pour vous faire part des évolutions de STUdio. Alors le voici : **le tout nouveau mécanisme d'annonces !**\n\nJe prévois de l'utiliser avec parcimonie, pour annoncer les fonctionnalités majeures et faire appel à vous occasionnellement (une version bêta va bientôt voir le jour). Ces annonces **ne s'afficheront qu'une fois**, et **vous pouvez les désactiver si vous le souhaitez**.\n\nÀ tous les packs d'histoires que vous créerez ! \uD83C\uDF7B";


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
        return configRetriever.getConfig();
    }

    public Future<JsonObject> latest() {
        LOGGER.debug("Search latest version");
        // Latest available release (from github)
        Promise<JsonObject> promise = Promise.promise();
        webClient
                .get(443, GITHUB_API_FQDN, GITHUB_API_LATEST_RELEASE)
                .ssl(true)
                .send(ar -> {
                    if (ar.succeeded()) {
                        promise.tryComplete(ar.result().bodyAsJsonObject());
                    } else {
                        promise.tryFail(ar.cause());
                    }
                });
        return promise.future();
    }

    public Future<JsonObject> announce() {
        Promise<JsonObject> promise = Promise.promise();
        LOGGER.debug("Search announce");
        // Get announce's last modification date (from github)
        webClient
                .get(443, GITHUB_API_FQDN, GITHUB_API_ANNOUNCE_COMMIT)
                .ssl(true)
                .send(ar -> {
                    if (ar.succeeded()) {
                        JsonArray commits = ar.result().bodyAsJsonArray();
                        if (commits.size() == 0) {
                             // First announce
                            promise.tryComplete(new JsonObject()
                                    .put("date", "2020-05-12T00:00:00.000Z")
                                    .put("content", GITHUB_API_BUILTIN_ANNOUNCE_CONTENT_EN + "\n\n-----\n\n" + GITHUB_API_BUILTIN_ANNOUNCE_CONTENT_FR)
                            );
                        } else {
                            String commitDate = commits.getJsonObject(0).getJsonObject("commit").getJsonObject("committer").getString("date");
                            // Get announce content (from github)
                            webClient
                                    .get(443, GITHUB_RAW_FQDN, GITHUB_API_ANNOUNCE_CONTENT)
                                    .ssl(true)
                                    .send(ar2 -> {
                                        if (ar2.succeeded()) {
                                            String content = ar2.result().bodyAsString();
                                            promise.tryComplete(new JsonObject().put("date", commitDate).put("content", content));
                                        } else {
                                            promise.tryFail(ar2.cause());
                                        }
                                    });
                        }
                    } else {
                        promise.tryFail(ar.cause());
                    }
                });
        return promise.future();
    }

}
