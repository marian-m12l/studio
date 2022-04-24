/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface IStoryTellerService {

    CompletionStage<JsonObject> deviceInfos();

    CompletionStage<JsonArray> packs();

    CompletionStage<Optional<String>> addPack(String uuid, Path packFile);

    CompletionStage<Boolean> deletePack(String uuid);

    CompletionStage<Boolean> reorderPacks(List<String> uuids);

    CompletionStage<Optional<String>> extractPack(String uuid, Path destFile);

    CompletionStage<Void> dump(Path outputPath);
}
