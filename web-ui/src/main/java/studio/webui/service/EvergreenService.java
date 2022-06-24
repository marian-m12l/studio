/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import static studio.webui.model.EvergreenDTOs.DEFAULT_ANNOUNCE_CONTENT;
import static studio.webui.model.EvergreenDTOs.DEFAULT_ANNOUNCE_DATE;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import studio.webui.model.EvergreenDTOs.AnnounceDTO;
import studio.webui.model.EvergreenDTOs.GithubClient;
import studio.webui.model.EvergreenDTOs.GithubRawClient;
import studio.webui.model.EvergreenDTOs.LatestVersionDTO;
import studio.webui.model.EvergreenDTOs.VersionDTO;

@ApplicationScoped
public class EvergreenService {

    private static final Logger LOGGER = LogManager.getLogger(EvergreenService.class);

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
            if (commits.isEmpty()) {
                LOGGER.debug("1st commmit -> 1st announce");
                var res = new AnnounceDTO(DEFAULT_ANNOUNCE_DATE, DEFAULT_ANNOUNCE_CONTENT);
                return CompletableFuture.completedStage(res);
            }
            // Return last commit date
            String commitDate = commits.get(0).getCommit().getCommitter().getDate();
            LOGGER.debug("Get announce content at {}", commitDate);
            // Get announce content (from github)
            return githubRawClient.announce().thenApply(content -> new AnnounceDTO(commitDate, content));
        });
    }
}
