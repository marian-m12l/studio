/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.util.List;
import java.util.Optional;

public interface IStoryTellerService {


    Optional<JsonObject> deviceInfos();

    JsonArray packs();

    Optional<String> addPack(String uuid, File packFile);

    boolean deletePack(String uuid);

    boolean reorderPacks(List<String> uuids);

    Optional<String> extractPack(String uuid, File destFile);
}
