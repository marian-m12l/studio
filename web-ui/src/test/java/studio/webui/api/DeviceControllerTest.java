package studio.webui.api;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import studio.core.v1.service.PackFormat;
import studio.core.v1.utils.io.FileUtils;
import studio.junit.TestNameExtension;
import studio.webui.model.DeviceDTOs.OutputDTO;
import studio.webui.model.DeviceDTOs.UuidDTO;
import studio.webui.model.DeviceDTOs.UuidsDTO;
import studio.webui.model.LibraryDTOs.TransferDTO;

@QuarkusTest
@TestHTTPEndpoint(DeviceController.class)
@ExtendWith(TestNameExtension.class)
class DeviceControllerTest {

    @ConfigProperty(name = "studio.library")
    Path libraryPath;

    @ConfigProperty(name = "studio.mock.device")
    Path devicePath;

    @Inject
    EventBus eventBus;

    // test raw pack name
    private static final String TEST_RAW_PACK_NAME = "SimplifiedSamplePack.pack";
    private static final String TEST_RAW_PACK_UUID = "60f84e3d-8a37-4b4a-9e67-fc13daad9bb9";
    private UuidDTO testUuidDto = new UuidDTO(TEST_RAW_PACK_UUID, TEST_RAW_PACK_NAME, PackFormat.RAW.getLabel());

    // test pack from src/test/resource
    private Path testPackSource;
    // test pack from device
    private Path testPackDevice;
    // test pack from library
    private Path testPackLibrary;

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        // empty library
        FileUtils.emptyDirectory(libraryPath);
        // empty device
        FileUtils.emptyDirectory(devicePath);
        // by default, add 1 test pack
        testPackSource = classpathResource(TEST_RAW_PACK_NAME);
        testPackDevice = devicePath.resolve(TEST_RAW_PACK_UUID + PackFormat.RAW.getExtension());
        testPackLibrary = libraryPath.resolve(TEST_RAW_PACK_NAME);
        Files.copy(testPackSource, testPackDevice);
        // log rest
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
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

    static void list1Pack() {
        when().get("packs") //
                .then().statusCode(200) //
                .body( //
                        "[0].format", is(PackFormat.RAW.getLabel()), //
                        "[0].uuid", is(TEST_RAW_PACK_UUID), //
                        "[0].timestamp", is(0), //
                        "[0].nightModeAvailable", is(false), //
                        "[0].official", is(false), //
                        "[0].sizeInBytes", is(0) //
                );
    }

    static void restSuccess(String operation, Object dto, boolean expectedState) {
        // Get SuccessDTO
        givenJson().body(dto) //
                .when().post(operation) //
                .then().statusCode(200) //
                .body("success", is(expectedState));
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
                        "driver", is(PackFormat.RAW.getLabel()), //
                        "uuid", is("mocked-device"), //
                        "serial", is("mocked-serial"), //
                        "firmware", is("mocked-version"), //
                        "error", is(false), //
                        "plugged", is(true) //
                );
    }

    @Test
    void testReorder() {
        UuidsDTO uuids = new UuidsDTO(Arrays.asList("123", "456"));
        restSuccess("reorder", uuids, false);
    }

    @Test
    void testDump() {
        OutputDTO output = new OutputDTO(libraryPath.resolve("dump.txt"));
        restSuccess("dump", output, true);
    }

    @Test
    void testRemoveFromDevice() {
        // list 1 test pack
        list1Pack();
        // remove pack
        restSuccess("removeFromDevice", testUuidDto, true);
        list0Pack();
        // don't need to remove fake pack
        UuidDTO fakeUuidDto = new UuidDTO(TEST_RAW_PACK_UUID, "fake.pack", PackFormat.RAW.getLabel());
        restSuccess("removeFromDevice", fakeUuidDto, false);
        list0Pack();
    }

    @Test
    void testAddToLibrary() throws IOException, InterruptedException {
        // KO: absent on device
        UuidDTO fakeUuidDto = new UuidDTO("1234", "fake.pack", PackFormat.FS.getLabel());
        restTransfer("addToLibrary", fakeUuidDto, false);

        // OK: copy from device to library
        restTransfer("addToLibrary", testUuidDto, true);
        Path newPack = libraryPath.resolve(TEST_RAW_PACK_UUID + PackFormat.RAW.getExtension());
        assertTrue(Files.exists(newPack), "Pack is absent from library");

        // KO: present on device
        restTransfer("addToLibrary", testUuidDto, true);
    }

    @Test
    void testAddFromLibrary() throws IOException, InterruptedException {
        // remove from device
        Files.deleteIfExists(testPackDevice);
        // add to library
        Files.copy(testPackSource, testPackLibrary);
        list0Pack();
        // copy from library to device
        restTransfer("addFromLibrary", testUuidDto, true);
        // list device
        list1Pack();
    }
}
