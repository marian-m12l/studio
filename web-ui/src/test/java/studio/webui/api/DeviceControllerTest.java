package studio.webui.api;

import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.given;

import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import studio.webui.model.DeviceDTOs.UuidDTO;

@QuarkusTest
@TestHTTPEndpoint(DeviceController.class)
class DeviceControllerTest {

//    @Test
    void testInfos() {
        when().get("infos") //
                .then().statusCode(200) //
                .body( //
                        "uuid", is("mocked-device"), //
                        "serial", is("mocked-serial"), //
                        "firmware", is("mocked-version"), //
                        "error", is(false), //
                        "plugged", is(true), //
                        "driver", is("raw") //
                ); //
    }

    @Test
    void testPacks() {
        when().get("packs") //
                .then().statusCode(200) //
                .body(is("[]"));

        UuidDTO fakeUuidDTO = new UuidDTO("123", "hello.pack", "raw");
        given().contentType(ContentType.JSON) //
                .body(fakeUuidDTO) //
                .when().post("removeFromDevice") //
                .then().statusCode(200) //
                .body("success", is(false));
    }
}
