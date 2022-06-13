/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

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

//    public static Router apiRouter(Router router, EvergreenService evergreenService) {
//        // Current version
//        router.get("/infos").handler(ctx -> evergreenService.infos());
//        // Future.fromCompletionStage(evergreenService.infos()) //
//                .onFailure(e -> {
//                    LOGGER.error("Failed to get current version infos");
//                    ctx.fail(500, e);
//                }).onSuccess(o -> {
//                    LOGGER.info("Current version infos: {}", o);
//                    ctx.json(o);
//                }) //
    // );
//                .thenAccept(maybeJson -> {
//            if (maybeJson.succeeded()) {
//                LOGGER.info("Current version infos: {}", maybeJson.result());
//                ctx.json(maybeJson.result());
//            } else {
//                LOGGER.error("Failed to get current version infos");
//                ctx.fail(500, maybeJson.cause());
//            }
//        })

    // Latest release
//        router.get("/latest").blockingHandler(ctx -> Future.fromCompletionStage(evergreenService.latest())//
//                .onFailure(e -> {
//                    LOGGER.error("Failed to get latest release");
//                    ctx.fail(500, e);
//                }).onSuccess(ctx::json) //
//        );
//                .onComplete(maybeJson -> {
//            if (maybeJson.succeeded()) {
//                ctx.json(maybeJson.result());
//            } else {
//                LOGGER.error("Failed to get latest release");
//                ctx.fail(500, maybeJson.cause());
//            }
//        }));

    // Announce
//        router.get("/announce").blockingHandler(ctx -> Future.fromCompletionStage(evergreenService.announce()) //
//                .onFailure(e -> {
//                    LOGGER.error("Failed to get announce");
//                    ctx.fail(500, e);
//                }).onSuccess(ctx::json) //
//        );
//                .onComplete(maybeJson -> {
//            if (maybeJson.succeeded()) {
//                ctx.json(maybeJson.result());
//            } else {
//                LOGGER.error("Failed to get announce");
//                ctx.fail(500, maybeJson.cause());
//            }
//        }));
//
//        return router;
//    }
}
