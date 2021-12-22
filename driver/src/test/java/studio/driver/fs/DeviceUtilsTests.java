package studio.driver.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class DeviceUtilsTests {

    private static String OS = System.getProperty("os.name").toLowerCase();
    public static boolean IS_WINDOWS = (OS.indexOf("win") >= 0);

    @Test
    void testWindows() throws IOException {
        assertEquals(IS_WINDOWS, DeviceUtils.isWindows(), "Is Windows ?");
    }

    @Test
    void testMountPoints() throws IOException, InterruptedException {
        // Unix only
        if (DeviceUtils.isWindows()) {
            return;
        }

        long t1 = System.currentTimeMillis();
        List<String> l1 = DeviceUtils.listMountPoints0();
        long t2 = System.currentTimeMillis();
        List<String> l2 = dfCommand();
        long t3 = System.currentTimeMillis();
        List<String> l3 = DeviceUtils.listMountPoints();
        long t4 = System.currentTimeMillis();

        System.out.println("listMountPoints:");
        System.out.printf("v1 (%s ms) : %s\n", t2 - t1, l1);
        System.out.printf("v2 (%s ms) : %s\n", t3 - t2, l2);
        System.out.printf("v3 (%s ms) : %s\n", t4 - t3, l3);

        assertEquals(l2, l3, "Different MountPoints");
    }

    /** Replace Runtime.exec with ProcessBuilder */
    private List<String> dfCommand() throws IOException, InterruptedException {
        final Process p = new ProcessBuilder("df", "-l").start();
        final Pattern dfPattern = Pattern.compile("^(\\/[^ ]+)[^/]+(/.*)$");

        return new BufferedReader(new InputStreamReader(p.getInputStream()))//
                .lines() //
                .map(dfPattern::matcher) //
                .filter(Matcher::matches) //
                .filter(m -> m.group(1).startsWith("/dev/")) //
                .map(m -> m.group(2)) //
                .collect(Collectors.toList());
    }

}