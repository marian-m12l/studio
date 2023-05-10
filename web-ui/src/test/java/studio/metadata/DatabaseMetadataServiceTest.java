package studio.metadata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import studio.junit.TestNameExtension;
import studio.metadata.DatabaseMetadataDTOs.DatabasePackMetadata;
import studio.metadata.DatabaseMetadataServiceTest.ResetDatabaseTestProfile;

@QuarkusTest
@ExtendWith(TestNameExtension.class)
@TestProfile(ResetDatabaseTestProfile.class)
class DatabaseMetadataServiceTest {

    @Inject
    DatabaseMetadataService ms;

    final DatabasePackMetadata metaOfficial = new DatabasePackMetadata(UUID.fromString("c4139d59-872a-4d15-8cf1-76d34cdf38c6"),
            "official", "official pack", null, true);
    final DatabasePackMetadata metaUnofficial = new DatabasePackMetadata(UUID.randomUUID(), "new", "new pack", null, false);
    final DatabasePackMetadata metaFake = new DatabasePackMetadata(UUID.randomUUID(), "fake", "fake pack", null, false);

    // Quarkus profile for resetting databases
    public static class ResetDatabaseTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("studio.db.reset", "true");
        }
    }

    private void assertPackMetadata(UUID uuid, boolean isOfficial, boolean isUnofficial) {
        assertAll("pack " + uuid, //
                () -> assertEquals(isOfficial, ms.getMetadataOfficial(uuid).isPresent(), "should be official"), //
                () -> assertEquals(isUnofficial, ms.getMetadataLibrary(uuid).isPresent(), "should be unofficial"), //
                () -> assertEquals(isOfficial || isUnofficial, ms.getMetadata(uuid).isPresent(), "should be a pack") //
        );
    }

    @Test
    void testDatabases() throws IOException {
        // GIVEN : started
        // WHEN : add unofficial pack
        ms.updateLibrary(metaUnofficial);
        // ms.persistLibrary();
        // THEN
        assertAll("metadata databases", //
                () -> assertPackMetadata(metaFake.getUuid(), false, false), //
                () -> assertPackMetadata(metaOfficial.getUuid(), true, false), //
                () -> assertPackMetadata(metaUnofficial.getUuid(), false, true) //
        );
    }

    @Test
    void testDatabasePackMetadata() throws IOException {
        // GIVEN : started
        DatabasePackMetadata mpActClone = metaUnofficial;
        DatabasePackMetadata metaUnofficial2 = new DatabasePackMetadata();
        metaUnofficial2.setUuid(metaUnofficial.getUuid());
        metaUnofficial2.setTitle(metaUnofficial.getTitle());
        metaUnofficial2.setDescription(metaUnofficial.getDescription());
        metaUnofficial2.setThumbnail(metaUnofficial.getThumbnail());
        metaUnofficial2.setOfficial(metaUnofficial.isOfficial());
        // WHEN : add unofficial pack
        ms.updateLibrary(metaUnofficial);
        // ms.persistLibrary();
        DatabasePackMetadata mpAct1 = ms.getMetadata(metaUnofficial.getUuid()).get();
        DatabasePackMetadata mpAct2 = ms.getMetadataLibrary(metaUnofficial.getUuid()).get();
        // THEN
        assertAll("DatabasePackMetadata", //
                () -> assertNotEquals(metaUnofficial, metaFake, "should differ by uuid"), //
                () -> assertFalse(metaUnofficial.isOfficial(), "should not be official"), //
                () -> assertEquals(mpActClone, metaUnofficial, "should be equal"), //
                () -> assertEquals(metaUnofficial, metaUnofficial2, "should be equal"), //
                () -> assertEquals(metaUnofficial, mpAct1, "should be equal"), //
                () -> assertEquals(metaUnofficial, mpAct2, "should be equal"), //
                () -> assertEquals(mpAct1, mpAct2, "should be equal"), //
                () -> assertEquals(metaUnofficial.toString(), metaUnofficial.toString(), "different toString()"), //
                () -> assertEquals(metaUnofficial.hashCode(), metaUnofficial.hashCode(), "different hashCode()") //
        );
    }

    @Test
    @Order(2)
    void testReload() throws Exception {
        // GIVEN : add 2 packs : unofficial and official
        ms.updateLibrary(metaUnofficial);
        ms.updateLibrary(metaOfficial);
        // THEN : official in library
        assertAll("metadata databases", //
                () -> assertPackMetadata(metaOfficial.getUuid(), true, false), // 1 official
                () -> assertPackMetadata(metaUnofficial.getUuid(), false, true) // 1 unofficial
        );
        // WHEN reload db (without reset)
        ms.setDbReset(false);
        ms.init(null);
        // THEN : no official in library
        assertAll("metadata databases", //
                () -> assertPackMetadata(metaOfficial.getUuid(), true, false), // 1 official
                () -> assertPackMetadata(metaUnofficial.getUuid(), false, false) // empty
        );
    }
}
