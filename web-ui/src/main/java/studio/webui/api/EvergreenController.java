/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.smallrye.common.annotation.NonBlocking;
import studio.webui.model.EvergreenDTOs.AnnounceDTO;
import studio.webui.model.EvergreenDTOs.LatestVersionDTO;
import studio.webui.model.EvergreenDTOs.VersionDTO;
import studio.webui.service.EvergreenService;

@Path("/api/evergreen")
public class EvergreenController {

    @Inject
    EvergreenService evergreenService;

    /** Installed version */
    @GET
    @Path("infos")
    @NonBlocking
    public VersionDTO infos() {
        return evergreenService.infos();
    }

    /** Latest available release (from github) */
    @GET
    @Path("latest")
    public CompletionStage<LatestVersionDTO> latest() {
        return evergreenService.latest();
    }

    /** Latest announce (from github). */
    @GET
    @Path("announce")
    public CompletionStage<AnnounceDTO> announce() {
        return evergreenService.announce();
    }
}
