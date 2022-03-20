package studio.driver.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class DeviceUtilsTests {

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = (OS.indexOf("win") >= 0);

    @Test
    void testWindows() throws IOException {
        assertEquals(IS_WINDOWS, DeviceUtils.isWindows(), "Is Windows ?");
    }

    @Test
    void testMountPoints() throws IOException {
        // Unix only
        if (DeviceUtils.isWindows()) {
            return;
        }

        long t1 = System.currentTimeMillis();
        List<Path> l1 = dfCommand();
        long t2 = System.currentTimeMillis();
        List<Path> l2 = DeviceUtils.listMountPoints();
        long t3 = System.currentTimeMillis();

        System.out.println("listMountPoints:");
        System.out.printf("v1 (%s ms) : %s\n", t2 - t1, l1);
        System.out.printf("v2 (%s ms) : %s\n", t3 - t2, l2);

        assertEquals(l1, l2, "Different MountPoints");
    }

    /** Replace Runtime.exec with ProcessBuilder */
    private List<Path> dfCommand() throws IOException {
        final Process p = new ProcessBuilder("df", "-l").start();
        final Pattern dfPattern = Pattern.compile("^(\\/[^ ]+)[^/]+(/.*)$");

        System.out.println("df -l");
        return new BufferedReader(new InputStreamReader(p.getInputStream()))//
                .lines() //
                .peek(System.out::println) // Debug
                .map(dfPattern::matcher) //
                .filter(Matcher::matches) //
                // Mounted devices only (without Fuse, Loopback...)
                .filter(m -> m.group(1).startsWith("/dev/s") || m.group(1).startsWith("/dev/disk")) //
                .map(m -> Path.of(m.group(2))) //
                .collect(Collectors.toList());
    }

}