/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import studio.webui.service.StoryTellerService;

import java.util.Optional;

public class DeviceController {

    public static Router apiRouter(Vertx vertx, StoryTellerService storyTellerService) {
        Router router = Router.router(vertx);

        // Plugged device metadata
        router.get("/infos").handler(ctx -> {
            Optional<JsonObject> maybeDeviceInfos = storyTellerService.deviceInfos();
            maybeDeviceInfos.ifPresentOrElse(
                    deviceInfos -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(deviceInfos)),
                    () -> ctx.fail(404)
            );
        });

        // Plugged device packs list
        router.get("/packs").handler(ctx -> {
            JsonArray devicePacks = storyTellerService.packs();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(devicePacks));
        });

        return router;
    }
}
