/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface IStoryTellerService {

    CompletableFuture<Optional<JsonObject>> deviceInfos();

    CompletableFuture<JsonArray> packs();

    CompletableFuture<Optional<String>> addPack(String uuid, Path packFile);

    CompletableFuture<Boolean> deletePack(String uuid);

    CompletableFuture<Boolean> reorderPacks(List<String> uuids);

    CompletableFuture<Optional<String>> extractPack(String uuid, Path destFile);

    CompletableFuture<Void> dump(Path outputPath);
}
