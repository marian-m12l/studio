/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import studio.webui.model.EvergreenDTOs.AnnounceDTO;
import studio.webui.model.EvergreenDTOs.CommitDto;
import studio.webui.model.EvergreenDTOs.LatestVersionDTO;
import studio.webui.model.EvergreenDTOs.VersionDTO;

@ApplicationScoped
public class EvergreenService {

    private static final Logger LOGGER = LogManager.getLogger(EvergreenService.class);

    private static final String ANNOUNCE_EN = "### Good news, everyone!\n\nSTUdio is improving, at a slow but steady pace, and that's primarily thanks to your feedback. Thanks for letting me know what's missing or broken (and also what's not \uD83D\uDE01)!\n\nTo better benefit from this feedback, I wanted a way to communicate directly to you and let you know how STUdio is evolving. So here it is: **a brand new announce mechanism!**\n\nI plan to use it sparsely, to announce major features and occasionally request your feedback (a beta version will be released soon). These announces **are only displayed once**, and **you can opt-out if you feel like it**.\n\nHere's to all the great story packs you're building! \uD83C\uDF7B";
    private static final String ANNOUNCE_FR = "### Good news, everyone!\n\nSTUdio s'améliore, lentement mais sûrement, et le mérite en revient grandement à tous vos retours. Merci de me faire savoir ce qu'il manque ou ce qui est cassé (et aussi ce qui ne l'est pas \uD83D\uDE01) !\n\nPour tirer avantage au mieux de vos retours, je souhaitais pouvoir communiquer directement avec vous pour vous faire part des évolutions de STUdio. Alors le voici : **le tout nouveau mécanisme d'annonces !**\n\nJe prévois de l'utiliser avec parcimonie, pour annoncer les fonctionnalités majeures et faire appel à vous occasionnellement (une version bêta va bientôt voir le jour). Ces annonces **ne s'afficheront qu'une fois**, et **vous pouvez les désactiver si vous le souhaitez**.\n\nÀ tous les packs d'histoires que vous créerez ! \uD83C\uDF7B";
    public static final String DEFAULT_ANNOUNCE_CONTENT = ANNOUNCE_EN + "\n\n-----\n\n" + ANNOUNCE_FR;
    public static final String DEFAULT_ANNOUNCE_DATE = "2020-05-12T00:00:00.000Z";

    @RegisterRestClient(baseUri = "https://api.github.com/repos/marian-m12l/studio")
    public interface GithubClient {
        @GET
        @Path("/releases/latest")
        CompletionStage<LatestVersionDTO> latestRelease();

        @GET
        @Path("/commits")
        CompletionStage<List<CommitDto>> commits(@QueryParam("path") String path);
    }

    @RegisterRestClient(baseUri = "https://raw.githubusercontent.com/marian-m12l/studio")
    public interface GithubRawClient {
        @GET
        @Path("/master/ANNOUNCE.md")
        CompletionStage<String> announce();
    }

    @ConfigProperty(name = "version")
    String version;

    @ConfigProperty(name = "timestamp")
    String timestamp;

    @RestClient
    GithubClient githubClient;

    @RestClient
    GithubRawClient githubRawClient;

    /** Current application version. */
    public VersionDTO infos() {
        return new VersionDTO(version, timestamp);
    }

    /** Latest available release (from github). */
    public CompletionStage<LatestVersionDTO> latest() {
        LOGGER.debug("Get latest version");
        return githubClient.latestRelease();
    }

    /** Get announce's last modification date (from github). */
    public CompletionStage<AnnounceDTO> announce() {
        LOGGER.debug("Search announce");
        return githubClient.commits("ANNOUNCE.md").thenCompose(commits -> {
            // 1st commmit -> 1st announce
            if (commits.size() == 0) {
                LOGGER.debug("1st commmit -> 1st announce");
                var res = new AnnounceDTO(DEFAULT_ANNOUNCE_DATE, DEFAULT_ANNOUNCE_CONTENT);
                return CompletableFuture.completedStage(res);
            }
            // Return last commit date
            String commitDate = commits.get(0).getCommit().getCommitter().getDate();
            LOGGER.debug("Get announce content at {}", commitDate);
            // Get announce content (from github)
            return githubRawClient.announce().thenApply(content -> {
                return new AnnounceDTO(commitDate, content);
            });
        });
    }
}
