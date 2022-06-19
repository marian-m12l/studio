package studio.metadata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DatabaseMetadataServiceTest {

    @ConfigProperty(name = "studio.db.official")
    Path officialDbPath;
    @ConfigProperty(name = "studio.db.unofficial")
    Path unofficialDbPath;

    @Inject
    DatabaseMetadataService ms;

    @BeforeEach
    void before() throws Exception {
        // clean json databases
        Files.deleteIfExists(officialDbPath);
        Files.deleteIfExists(unofficialDbPath);
    }

    @Test
    void testMetadataService() throws Exception {
        // GIVEN & WHEN : init DatabaseMetadataService
        // THEN : missing uuid
        String fakeUuid = "0-0-0-0";
        System.out.println("Test db with uuid " + fakeUuid);
        assertAll("unofficial pack " + fakeUuid, //
                () -> assertTrue(ms.getOfficialMetadata(fakeUuid).isEmpty(), "should not be official pack"), //
                () -> assertTrue(ms.getUnofficialMetadata(fakeUuid).isEmpty(), "should not unofficial pack"), //
                () -> assertTrue(ms.getPackMetadata(fakeUuid).isEmpty(), "should not be a pack") //
        );

        // WHEN : new uuid
        String newUuid = "1-2-3-4";
        System.out.println("Test db with uuid " + newUuid);
        DatabasePackMetadata mpExp = new DatabasePackMetadata(newUuid, "fake", "fake pack", null, false);
        DatabasePackMetadata mpBadId = new DatabasePackMetadata("badId", "fake", "fake pack", null, false);
        // add and write to disk
        ms.refreshUnofficialCache(mpExp);
        ms.persistUnofficialDatabase();
        // THEN
        assertTrue(ms.getOfficialMetadata(newUuid).isEmpty(), "should not be official pack");
        DatabasePackMetadata mpAct = ms.getPackMetadata(newUuid).get();
        DatabasePackMetadata mpAct2 = ms.getUnofficialMetadata(newUuid).get();
        DatabasePackMetadata mpActClone = mpAct;

        assertAll("unofficial pack " + newUuid, //
                () -> assertNotEquals(mpBadId, mpAct, "should differ by uuid"), //
                () -> assertFalse(mpAct.isOfficial(), "should not be official"), //
                () -> assertEquals(mpActClone, mpAct, "differs from itself"), //
                () -> assertEquals(mpExp, mpAct, "differs from expected"), //
                () -> assertEquals(mpExp, mpAct2, "differs from unoffical db"), //
                () -> assertEquals(mpAct, mpAct2, "differs from each other"), //
                () -> assertEquals(mpExp.toString(), mpAct.toString(), "different toString()"), //
                () -> assertEquals(mpExp.hashCode(), mpAct.hashCode(), "different hashCode()") //
        );

        // WHEN reload db
        ms.init(null);
        // THEN
        assertEquals(mpExp, ms.getUnofficialMetadata(newUuid).get(), "differs from expected");
    }

}
