package studio.driver;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.model.TransferStatus;

public interface StoryTellerAsyncDriver<T, U> {

    void registerDeviceListener(DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener);

    CompletionStage<T> getDeviceInfos();

    CompletionStage<List<U>> getPacksList();

    CompletionStage<Boolean> reorderPacks(List<String> uuids);

    CompletionStage<Boolean> deletePack(String uuid);

    CompletionStage<TransferStatus> downloadPack(String uuid, Path destPath, TransferProgressListener listener);

    CompletionStage<TransferStatus> uploadPack(String uuid, Path inputPath, TransferProgressListener listener);

    CompletionStage<Void> dump(Path outputPath);

}
