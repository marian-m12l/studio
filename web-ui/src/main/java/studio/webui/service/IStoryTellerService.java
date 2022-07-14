/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.MetaPackDTO;

public interface IStoryTellerService {

    CompletionStage<DeviceInfosDTO> deviceInfos();

    CompletionStage<List<MetaPackDTO>> packs();

    CompletionStage<String> addPack(String uuid, Path packFile);

    CompletionStage<Boolean> deletePack(String uuid);

    CompletionStage<Boolean> reorderPacks(List<String> uuids);

    CompletionStage<String> extractPack(String uuid, Path destFile);

    CompletionStage<Void> dump(Path outputPath);

    default void sendDevicePlugged(EventBus eventBus, DeviceInfosDTO infosDTO) {
        eventBus.publish("storyteller.plugged", JsonObject.mapFrom(infosDTO));
    }

    default void sendDeviceUnplugged(EventBus eventBus) {
        eventBus.publish("storyteller.unplugged", null);
    }

    default void sendFailure(EventBus eventBus) {
        eventBus.publish("storyteller.failure", null);
    }

    default void sendProgress(EventBus eventBus, String id, double p) {
        eventBus.publish("storyteller.transfer." + id + ".progress", new JsonObject().put("progress", p));
    }

    default void sendDone(EventBus eventBus, String id, boolean success) {
        eventBus.publish("storyteller.transfer." + id + ".done", new JsonObject().put("success", success));
    }
}
