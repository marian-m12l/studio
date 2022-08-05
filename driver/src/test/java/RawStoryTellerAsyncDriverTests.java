import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.driver.service.raw.RawStoryTellerAsyncDriver;

class RawStoryTellerAsyncDriverTests {

    private static boolean checkUuidBytes(long highBytes, long lowBytes) {
        return (highBytes != 0L || lowBytes != 0L) && (highBytes != -1L || lowBytes != -1L)
                && (lowBytes != -4294967296L || highBytes != -4294967296L);
    }

    @Test
    void validBytes() {
        List<Long> values = Arrays.asList(-4294967296L, -2L, -1L, 0L, 1L, 2L, 42L);
        for (Long i : values) {
            for (Long j : values) {
                assertEquals(checkUuidBytes(i, j), RawStoryTellerAsyncDriver.checkUuidBytes(i, j),
                        String.format("checkUuidBytes(%d,%d)", i, j));
            }
        }
    }
}
