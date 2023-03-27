package studio.driver.service.fs;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.usb4java.Device;

import lombok.extern.slf4j.Slf4j;
import studio.core.v1.exception.NoDevicePluggedException;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.model.TransferListener.TransferProgressListener;
import studio.core.v1.service.fs.FsStoryPackDTO.SdPartition;
import studio.core.v1.utils.io.FileUtils;
import studio.driver.model.DeviceInfosDTO;

@Slf4j
class FsStoryTellerAsyncDriverTests {

    // test pack
    private static final UUID TEST_PACK_UUID = UUID.fromString("60f84e3d-8a37-4b4a-9e67-fc13daad9bb9");
    private static final String TEST_PACK_NAME = TEST_PACK_UUID + ".converted_1678048354561";
    private static final UUID FAKE_PACK_UUID = UUID.randomUUID();

    static Path studioHome = Path.of("./target/studio");
    static Path devicePath = studioHome.resolve("device/");
    static Path libraryPath = studioHome.resolve("library/");

    static Device fakeDevice = Mockito.mock(Device.class); // final class, mockito-inline needed
    static SdPartition fakePartition = new SdPartition(devicePath);
    static String noDevice = new NoDevicePluggedException().getMessage();

    FsStoryTellerAsyncDriver fd = new FsStoryTellerAsyncDriver();

    static TransferProgressListener progressListener = status -> {
        double p = status.getPercent();
        if (log.isInfoEnabled()) {
            log.info("Transferring {} ({} / {})", //
                    FileUtils.readablePercent(p), //
                    FileUtils.readableByteSize(status.getTransferred()), //
                    FileUtils.readableByteSize(status.getTotal()));
        }
    };

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        // empty library and device
        FileUtils.emptyDirectory(devicePath);
        FileUtils.emptyDirectory(libraryPath);
        Files.createDirectory(fakePartition.getContentFolder());
        // by default, add 1 test pack
        Path testPackSource = classpathResource(TEST_PACK_NAME);
        Path testPackDevice = fakePartition.getPackFolder(TEST_PACK_UUID);
        FileUtils.copyFolder(UUID.randomUUID(), testPackSource, testPackDevice, null);
    }

    void startDevice() {
        fd.setDevice(fakeDevice);
        fd.setSdPartition(fakePartition);
        fd.writePackIndex(Arrays.asList(TEST_PACK_UUID));
        fd.writeDeviceInfo(fakePartition.getDeviceMetada(), (short) 2, (short) 2, (short) 1, //
                "1234567", new byte[256]);
    }

    @AfterEach
    void stopDevice() {
        fd.setDevice(null);
        fd.setSdPartition(null);
    }

    @Test
    void deviceInfos() {
        // no device
        assertThrowsAsync(noDevice, fd.getDeviceInfos());
        // plug
        startDevice();
        DeviceInfosDTO di = join(fd.getDeviceInfos());
        assertAll("Device info", //
                () -> assertEquals("fs", di.getDriver()), //
                () -> assertEquals(true, di.isPlugged()), //
                () -> assertEquals("1.2", di.getFirmware()), //
                () -> assertEquals("00000001234567", di.getSerial()) //
        // () -> assertEquals(new byte[256], di.getDeviceKey()) //
        );
    }

    @Test
    void getPackList() {
        // no device
        assertThrowsAsync(noDevice, fd.getPacksList());
        // plug device with 1 pack
        startDevice();
        assertPacksSize(1);
    }

    @Test
    void reorderPacks() {
        // no device
        assertThrowsAsync(noDevice, fd.reorderPacks(null));
        // unknown id
        startDevice();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        var newList = Arrays.asList(u2, u1);
        assertThrowsAsync("Packs on device do not match UUIDs", fd.reorderPacks(newList));
        // sort ok
        fd.writePackIndex(Arrays.asList(u1, u2));
        assertTrue(join(fd.reorderPacks(newList)));
        assertEquals(newList, fd.readPackIndex());
    }

    @Test
    void deletePack() {
        // no device
        assertThrowsAsync(noDevice, fd.deletePack(null));
        // start
        startDevice();
        assertPacksSize(1);
        // unknown id
        assertThrowsAsync("Pack not found", fd.deletePack(UUID.randomUUID()));
        // 0 pack
        join(fd.deletePack(TEST_PACK_UUID));
        assertPacksSize(0);
    }

    @Test
    void downloadPack() {
        assertThrowsAsync(noDevice, fd.downloadPack(null, null, null));
        // start
        startDevice();
        // ko
        assertThrows(StoryTellerException.class,
                () -> fd.downloadPack(FAKE_PACK_UUID, libraryPath, progressListener));
        // ok
        join(fd.downloadPack(TEST_PACK_UUID, libraryPath, progressListener));
        assertTrue(Files.exists(libraryPath.resolve(TEST_PACK_UUID.toString())));
    }

    @Test
    void uploadPack() throws URISyntaxException {
        assertThrowsAsync(noDevice, fd.uploadPack(null, null, null));
        // start
        startDevice();
        join(fd.deletePack(TEST_PACK_UUID));
        // ko
        Path fakePath = libraryPath.resolve(TEST_PACK_NAME);
        assertThrows(StoryTellerException.class,
                () -> fd.uploadPack(TEST_PACK_UUID, fakePath, progressListener));
        // ok
        join(fd.uploadPack(TEST_PACK_UUID, classpathResource(TEST_PACK_NAME), progressListener));
        assertPacksSize(1);
    }

    @Test
    void dump() {
        assertNull(join(fd.dump(null)));
    }

    public static <T> void assertThrowsAsync(String expectedMsg, CompletionStage<T> cs) {
        Throwable exception = assertThrows(CompletionException.class, cs.toCompletableFuture()::join);
        assertEquals(expectedMsg, exception.getCause().getMessage());
    }

    public static <T> T join(CompletionStage<T> cs) {
        return cs.toCompletableFuture().join();
    }

    void assertPacksSize(int size) {
        var packList = join(fd.getPacksList());
        assertEquals(size, packList.size());
    }

    static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }
}