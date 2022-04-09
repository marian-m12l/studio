package studio.metadata;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import studio.config.StudioConfig;

class DatabaseMetadataServiceTest {

    @Test
    void testMetadataService() throws Exception {
        // GIVEN
        Path target = Path.of("./target");
        Path officialDbPath = target.resolve("official.json");
        Path unofficialDbPath = target.resolve("unofficial.json");
        // clean db
        Files.deleteIfExists(officialDbPath);
        Files.deleteIfExists(unofficialDbPath);

        // WHEN
        DatabaseMetadataService ms = //
                withEnvironmentVariable(StudioConfig.STUDIO_DB_OFFICIAL.name(), officialDbPath.toString()) //
                        .and(StudioConfig.STUDIO_DB_UNOFFICIAL.name(), unofficialDbPath.toString()) //
                        .execute(DatabaseMetadataService::new);

        // THEN
        String fakeUuid = "0-0-0-0";
        System.out.println("Test db with uuid " + fakeUuid);
        assertAll("unofficial pack " + fakeUuid, //
                () -> assertTrue(ms.getOfficialMetadata(fakeUuid).isEmpty(), "should not be official pack"), //
                () -> assertTrue(ms.getUnofficialMetadata(fakeUuid).isEmpty(), "should not unofficial pack"), //
                () -> assertTrue(ms.getPackMetadata(fakeUuid).isEmpty(), "should not be a pack") //
        );
        // WHEN
        String newUuid = "1-2-3-4";
        System.out.println("Test db with uuid " + newUuid);
        DatabasePackMetadata mpExp = new DatabasePackMetadata(newUuid, "fake", "fake pack", null, false);
        // add and write to disk
        ms.refreshUnofficialCache(mpExp);
        ms.persistUnofficialDatabase();
        // THEN
        assertTrue(ms.getOfficialMetadata(newUuid).isEmpty(), "should not be official pack");

        DatabasePackMetadata mpAct = ms.getPackMetadata(newUuid).get();
        DatabasePackMetadata mpAct2 = ms.getUnofficialMetadata(newUuid).get();
        assertAll("unofficial pack " + newUuid, //
                () -> assertNotEquals(null, mpAct, "should not be null"), //
                () -> assertFalse(mpAct.isOfficial(), "should not be official"), //
                () -> assertEquals(mpExp, mpAct, "differs from expected"), //
                () -> assertEquals(mpExp, mpAct2, "differs from unoffical db"), //
                () -> assertEquals(mpExp.toString(), mpAct.toString(), "different toString()"), //
                () -> assertEquals(mpExp.hashCode(), mpAct.hashCode(), "different hashCode()") //
        );

        // reload db
        DatabaseMetadataService ms2 = withEnvironmentVariable(StudioConfig.STUDIO_DB_OFFICIAL.name(),
                officialDbPath.toString()) //
                        .and(StudioConfig.STUDIO_DB_UNOFFICIAL.name(), unofficialDbPath.toString()) //
                        .execute(DatabaseMetadataService::new);
        assertEquals(mpExp, ms2.getUnofficialMetadata(newUuid).get(), "differs from expected");
    }

}
