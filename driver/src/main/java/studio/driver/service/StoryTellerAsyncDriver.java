/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.MetaPackDTO;
import studio.driver.model.TransferStatus;

public interface StoryTellerAsyncDriver {

    boolean hasDevice();

    void registerDeviceListener(DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener);

    CompletionStage<DeviceInfosDTO> getDeviceInfos();

    CompletionStage<List<MetaPackDTO>> getPacksList();

    CompletionStage<Boolean> reorderPacks(List<UUID> uuids);

    CompletionStage<Boolean> deletePack(UUID uuid);

    CompletionStage<TransferStatus> downloadPack(UUID uuid, Path destPath, TransferProgressListener listener);

    CompletionStage<TransferStatus> uploadPack(UUID uuid, Path inputPath, TransferProgressListener listener);

    CompletionStage<Void> dump(Path outputPath);
}
