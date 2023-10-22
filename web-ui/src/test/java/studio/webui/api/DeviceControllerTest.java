package studio.webui.api;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.usb4java.Device;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import studio.core.v1.service.PackFormat;
import studio.core.v1.utils.io.FileUtils;
import studio.driver.service.fs.FsStoryTellerAsyncDriver;
import studio.junit.TestNameExtension;
import studio.webui.model.DeviceDTOs.TransferDTO;
import studio.webui.model.DeviceDTOs.OutputDTO;
import studio.webui.model.DeviceDTOs.UuidDTO;
import studio.webui.model.DeviceDTOs.UuidsDTO;
import studio.webui.service.DeviceService;
import studio.core.v1.service.fs.FsStoryPackDTO.SdPartition;

@QuarkusTest
@TestHTTPEndpoint(DeviceController.class)
@ExtendWith(TestNameExtension.class)
class DeviceControllerTest {

    @ConfigProperty(name = "studio.library")
    Path libraryPath;

    @ConfigProperty(name = "studio.mock.device")
    Path devicePath;

    @Inject
    DeviceService deviceService;

    @Inject
    EventBus eventBus;

    // test fs pack
    static final UUID TEST_PACK_UUID = UUID.fromString("60f84e3d-8a37-4b4a-9e67-fc13daad9bb9");
    static final String TEST_PACK_NAME = TEST_PACK_UUID + ".converted_1678048354561";
    static final UuidDTO testUuidDto = new UuidDTO(TEST_PACK_UUID, TEST_PACK_NAME, PackFormat.FS.getLabel());
    static final UuidDTO fakeUuidDto = new UuidDTO(UUID.randomUUID(), "fakePack", PackFormat.FS.getLabel());

    Device fakeDevice = Mockito.mock(Device.class, withSettings().mockMaker(MockMakers.INLINE)); // final class, with mockito-inline
    SdPartition fakePartition;

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        // empty library and device
        FileUtils.emptyDirectory(devicePath);
        FileUtils.emptyDirectory(libraryPath);
        fakePartition = new SdPartition(devicePath);
        Files.createDirectory(fakePartition.getContentFolder());
        // by default, add 1 test pack
        Path testPackSource = classpathResource(TEST_PACK_NAME);
        Path testPackDevice = fakePartition.getPackFolder(TEST_PACK_UUID);
        FileUtils.copyFolder(UUID.randomUUID(), testPackSource, testPackDevice, null);
        // start device
        startDevice();
        // log rest
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    void startDevice() {
        FsStoryTellerAsyncDriver fd = deviceService.getFsDriver();
        fd.setDevice(fakeDevice);
        fd.setSdPartition(fakePartition);
        fd.writePackIndex(Arrays.asList(TEST_PACK_UUID));
        fd.writeDeviceInfo(fakePartition.getDeviceMetada(), (short) 2, (short) 2, (short) 1, //
                "1234567", new byte[256]);
    }

    @AfterEach
    void stopDevice() {
        FsStoryTellerAsyncDriver fd = deviceService.getFsDriver();
        fd.setDevice(null);
        fd.setSdPartition(null);
    }

    static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }

    static RequestSpecification givenJson() {
        return given().contentType(ContentType.JSON);
    }

    static void list0Pack() {
        when().get("packs") //
                .then().statusCode(200) //
                .body(is("[]"));
    }

    void list1Pack() throws IOException {
        long size = FileUtils.getFolderSize(fakePartition.getPackFolder(TEST_PACK_UUID));
        when().get("packs") //
                .then().statusCode(200) //
                .body( //
                        "[0].format", is(PackFormat.FS.getLabel()), //
                        "[0].uuid", is(TEST_PACK_UUID.toString()), //
                        "[0].timestamp", is(0), //
                        "[0].nightModeAvailable", is(false), //
                        "[0].official", is(false), //
                        "[0].sizeInBytes", is((int)size) //
                );
    }

    void restSuccess(String operation, Object dto, boolean expectedState) {
        givenJson().body(dto) //
                .when().post(operation) //
                .then().statusCode(200) //
                .body("success", is(expectedState));
    }

    void restError(String operation, Object dto, String expectedError) {
        givenJson().body(dto) //
                .when().post(operation) //
                .then().statusCode(500) //
                .body("details", containsString(expectedError));
    }

    void restTransfer(String operation, UuidDTO uuidDto, boolean expectedState) throws InterruptedException {
        // Get TransferDTO
        TransferDTO transferDTO = givenJson().body(uuidDto) //
                .when().post(operation) //
                .then().statusCode(200) //
                .extract().as(TransferDTO.class);
        // Wait for transfer
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean();
        eventBus.localConsumer("storyteller.transfer." + transferDTO.getTransferId() + ".progress", m -> {
            System.out.println("progress: " + m.body());
        });
        eventBus.localConsumer("storyteller.transfer." + transferDTO.getTransferId() + ".done", m -> {
            JsonObject jo = (JsonObject) m.body();
            System.out.println("done: " + jo);
            success.set(jo.getBoolean("success"));
            latch.countDown();
        });
        latch.await(10, TimeUnit.SECONDS);
        // test
        assertEquals(expectedState, success.get(), "transfer should be " + expectedState);
    }

    @Test
    void testInfos() {
        when().get("infos") //
                .then().statusCode(200) //
                .body( //
                        "driver", is(PackFormat.FS.getLabel()), //
                        "firmware", is("1.2"), //
                        "serial", is("00000001234567"), //
                        "error", is(false), //
                        "plugged", is(true) //
                );
    }

    @Test
    void testReorder() {
        // KO
        UuidsDTO uuids2 = new UuidsDTO(Arrays.asList(fakeUuidDto.getUuid()));
        restError("reorder", uuids2, "Packs on device do not match UUIDs");
        // OK
        UuidsDTO uuids1 = new UuidsDTO(Arrays.asList(testUuidDto.getUuid()));
        restSuccess("reorder", uuids1, true);
    }

    @Test
    void testDump() {
        OutputDTO output = new OutputDTO(libraryPath.resolve("dump.txt"));
        restSuccess("dump", output, true);
    }

    @Test
    void testRemoveFromDevice() throws IOException {
        // list 1 test pack
        list1Pack();
        // KO: don't need to remove fake pack
        restError("removeFromDevice", fakeUuidDto, "Pack not found");
        list1Pack();
        // OK: remove pack
        restSuccess("removeFromDevice", testUuidDto, true);
        list0Pack();
    }

    @Test
    void testAddToLibrary() throws IOException, InterruptedException {
        // downloaded pack name is uuid
        Path newPath = libraryPath.resolve(TEST_PACK_UUID.toString());
        // Testing : Copy from device to library
        // KO: absent on device
        restTransfer("addToLibrary", fakeUuidDto, false);
        assertTrue(Files.notExists(newPath), "Test pack present " + newPath);
        // OK: present on device
        restTransfer("addToLibrary", testUuidDto, true);
        assertTrue(Files.exists(newPath), "Test pack absent " + newPath);
    }

    @Test
    void testAddFromLibrary() throws IOException, InterruptedException, URISyntaxException {
        restSuccess("removeFromDevice", testUuidDto, true);
        list0Pack();
        // Testing : Copy from library to device
        // KO: absent on library
        restTransfer("addFromLibrary", fakeUuidDto, false);
        list0Pack();
        // OK: present on library
        FileUtils.copyFolder(UUID.randomUUID(), classpathResource(TEST_PACK_NAME), libraryPath.resolve(TEST_PACK_NAME), null);
        restTransfer("addFromLibrary", testUuidDto, true);
        list1Pack();
    }
}
