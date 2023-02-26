package studio.driver.service.fs;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FsStoryTellerAsyncDriverTests {

    @Test
    void areOnDevice() {
        var u1 = UUID.randomUUID();
        var u2 = UUID.randomUUID();
        var u3 = UUID.randomUUID();
        var uuids = Arrays.asList(u1, u2);
        var packUUIDs = Arrays.asList(u1, u2, u3);
        System.err.println("uuids " + uuids);
        System.err.println("packuuids " + packUUIDs);
        boolean areOnDevice1 = uuids.stream().allMatch(uuid -> packUUIDs.stream().anyMatch(uuid::equals));
        boolean areOnDevice2 = uuids.stream().allMatch(packUUIDs::contains);
        boolean areOnDevice3 = packUUIDs.containsAll(uuids);
        assertAll( "areOnDevice", //
           () -> assertEquals(true, areOnDevice1), //
           () -> assertEquals(true, areOnDevice2), //
           () -> assertEquals(true, areOnDevice3) //
        );
    }
}