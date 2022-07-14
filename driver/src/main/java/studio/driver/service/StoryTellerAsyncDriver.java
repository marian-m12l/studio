package studio.driver.service;

import java.nio.file.Path;
import java.util.List;
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

    CompletionStage<Boolean> reorderPacks(List<String> uuids);

    CompletionStage<Boolean> deletePack(String uuid);

    CompletionStage<TransferStatus> downloadPack(String uuid, Path destPath, TransferProgressListener listener);

    CompletionStage<TransferStatus> uploadPack(String uuid, Path inputPath, TransferProgressListener listener);

    CompletionStage<Void> dump(Path outputPath);
}
