package studio.webui.api;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import studio.core.v1.service.PackFormat;
import studio.core.v1.utils.io.FileUtils;
import studio.junit.TestNameExtension;
import studio.webui.model.LibraryDTOs.PackDTO;
import studio.webui.model.LibraryDTOs.PathDTO;
import studio.webui.model.LibraryDTOs.SuccessPathDTO;

@QuarkusTest
@TestHTTPEndpoint(LibraryController.class)
@ExtendWith(TestNameExtension.class)
class LibraryControllerTest {

    @ConfigProperty(name = "studio.library")
    Path libraryPath;

    // test pack name
    private static final String TEST_PACK_NAME = "SimplifiedSamplePack.zip";
    // test pack from src/test/resource
    private Path testPackSource;
    // test pack from library
    private Path testPackLibrary;

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        // empty library
        FileUtils.emptyDirectory(libraryPath);
        // by default, add 1 test pack
        testPackSource = classpathResource(TEST_PACK_NAME);
        testPackLibrary = libraryPath.resolve(TEST_PACK_NAME);
        Files.copy(testPackSource, testPackLibrary);
        // log rest
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
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
                        "[0].uuid", notNullValue(), //
                        "[0].packs", notNullValue(), //

                        "[0].packs[0].path", is(TEST_PACK_NAME), //
                        "[0].packs[0].format", is(PackFormat.ARCHIVE.getLabel()), //
                        "[0].packs[0].uuid", notNullValue(), //
                        "[0].packs[0].title", is("SimplifiedSamplePack"), //
                        "[0].packs[0].nightModeAvailable", is(false), //
                        "[0].packs[0].official", is(false), //
                        "[0].packs[0].sizeInBytes", is(0) //
                );
    }

    static void removePack(String packName) {
        PathDTO pathDto = new PathDTO(packName);

        given().contentType(ContentType.JSON).body(pathDto) //
                .when().post("remove") //
                .then().statusCode(200) //
                .body("success", is(true));
    }

    @Test
    void testInfos() {
        when().get("infos") //
                .then().statusCode(200) //
                .body("path", is(libraryPath.toString()));
    }

    @Test
    void testRemove() {
        // list test pack
        list1Pack();
        // remove pack
        removePack(TEST_PACK_NAME);
        list0Pack();
        // don't need to remove fake pack
        removePack("fake.pack");
        list0Pack();
    }

    @Test
    void testUpload() throws IOException {
        // upload
        given().multiPart("pack", testPackSource.toFile()) //
                .when().post("upload") //
                .then().statusCode(200).body("success", is(true));
        // list 1 pack
        list1Pack();

        // compare file
        byte[] expContent = Files.readAllBytes(testPackSource);
        byte[] actualContent = Files.readAllBytes(testPackLibrary);
        assertEquals(Arrays.mismatch(expContent, actualContent), -1, "Different file download");
    }

    @Test
    void testDownload() throws IOException {
        // download
        PathDTO pathDto = new PathDTO(TEST_PACK_NAME);

        byte[] actualContent = given().contentType(ContentType.JSON).body(pathDto) //
                .when().post("download") //
                .then().statusCode(200) //
                .extract().asByteArray();
        // compare file
        byte[] expContent = Files.readAllBytes(testPackLibrary);
        assertEquals(Arrays.mismatch(expContent, actualContent), -1, "Different file download");
    }

    @Test
    void testConvertPack() {
        // convert pack
        PackDTO packDTO = new PackDTO();
        packDTO.setPath(TEST_PACK_NAME);
        packDTO.setFormat(PackFormat.RAW.getLabel());
        packDTO.setAllowEnriched(true);

        SuccessPathDTO successPathDTO = given().contentType(ContentType.JSON).body(packDTO) //
                .when().post("convert") //
                .then().statusCode(200) //
                .body("success", is(true)) //
                .extract().as(SuccessPathDTO.class); //

        assertTrue(Files.exists(Path.of(successPathDTO.getPath())));

        // list 2 packs
        when().get("packs") //
                .then().statusCode(200) //
                .body( //
                        "[0].uuid", notNullValue(), //
                        "[0].packs", notNullValue(), //

                        "[0].packs[0].path", endsWith(PackFormat.RAW.getExtension()), //
                        "[0].packs[0].format", is(PackFormat.RAW.getLabel()), //
                        "[0].packs[0].uuid", notNullValue(), //
                        "[0].packs[0].title", is("SimplifiedSamplePack"), //
                        "[0].packs[0].nightModeAvailable", is(false), //
                        "[0].packs[0].official", is(false), //
                        "[0].packs[0].sizeInBytes", is(0), //

                        "[0].packs[1].path", is(TEST_PACK_NAME), //
                        "[0].packs[1].format", is(PackFormat.ARCHIVE.getLabel()), //
                        "[0].packs[1].uuid", notNullValue(), //
                        "[0].packs[1].title", is("SimplifiedSamplePack"), //
                        "[0].packs[1].nightModeAvailable", is(false), //
                        "[0].packs[1].official", is(false), //
                        "[0].packs[1].sizeInBytes", is(0) //
                );
    }

}
