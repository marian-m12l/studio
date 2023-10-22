package studio.core.v1.utils.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import studio.core.v1.exception.StoryTellerException;

class FileUtilsTests {

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = (OS.indexOf("win") >= 0);
    private static final String CONTENT = "Hello World";

    @TempDir
    Path tmp;

    @Test
    void testWindows() throws IOException {
        assertEquals(IS_WINDOWS, FileUtils.isWindows(), "Is Windows ?");
    }

    @Test
    void testMountPoints() throws IOException {
        // Unix only
        if (FileUtils.isWindows()) {
            return;
        }

        long t1 = System.currentTimeMillis();
        List<Path> l1 = dfCommand();
        long t2 = System.currentTimeMillis();
        List<Path> l2 = FileUtils.listMountPoints();
        long t3 = System.currentTimeMillis();

        System.out.println("listMountPoints:");
        System.out.printf("v1 (%s ms) : %s\n", t2 - t1, l1);
        System.out.printf("v2 (%s ms) : %s\n", t3 - t2, l2);

        assertEquals(l1, l2, "Different MountPoints");
    }

    @Test
    void fileSize() throws IOException {
        Path f1 = addFile(tmp, "f1.txt");
        long fileSize = FileUtils.getFileSize(f1);
        System.out.printf("File size %s : %s bytes \n", f1, fileSize);
        assertEquals(CONTENT.length(), fileSize, "Different file size");
    }

    @Test
    void readableByteSize() {
        assertReadableByteSize("11 bytes", 11);
        assertReadableByteSize("1.0 KB", 1024);
        assertReadableByteSize("4.0 KB", 4096);
        assertReadableByteSize("976.56 KB", 1_000_000);
        assertReadableByteSize("3.81 MB", 4_000_000);
    }

    @Test
    void folderSize() throws IOException {
        // empty
        assertFolderSize(tmp, 0);
        // add empty folders
        Path dir1 = addDir(tmp, "dir1/");
        Path subdir1 = addDir(dir1, "subdir1/");
        Path dir2 = addDir(tmp, "dir2/");
        assertFolderSize(tmp, 0);
        // add small file
        addFile(tmp, "f1.txt");
        assertFolderSize(tmp, CONTENT.length());
        // add small other files
        addFile(subdir1, "f2.txt");
        addFile(dir2, "f2.txt");
        assertFolderSize(tmp, CONTENT.length() * 3);
    }

    @Test
    void createDirectories() throws IOException {
        // Missing Directory -> OK
        Path helloDir = tmp.resolve("hello");
        FileUtils.createDirectories("Failed to init hello dir", helloDir);
        // Existing Directory -> OK
        FileUtils.createDirectories("Failed to init hello dir", helloDir);

        // Existing File -> KO
        Path helloFile = Files.createFile(helloDir.resolve("hello.txt"));
        String errorMessage = "Failed to init hello dir";
        StoryTellerException e = assertThrows(StoryTellerException.class, () -> {
            FileUtils.createDirectories(errorMessage, helloFile);
        });
        assertEquals(errorMessage, e.getMessage(), "Invalid errorMessage");
    }

    Path addFile(Path dir, String name) throws IOException {
        Path f = Files.writeString(dir.resolve(name), CONTENT);
        System.out.printf("  Add %s\n", tmp.relativize(f));
        return f;
    }

    Path addDir(Path dir, String name) throws IOException {
        Path d = Files.createDirectory(dir.resolve(name));
        System.out.printf("  Add %s/\n", tmp.relativize(d));
        return d;
    }

    void assertFolderSize(Path dir, long expectedSize) throws IOException {
        long folderSize = FileUtils.getFolderSize(dir);
        System.out.printf("Dir size %s : %s bytes \n", dir, folderSize);
        assertEquals(expectedSize, folderSize, "Different directory size");
    }

    void assertReadableByteSize(String expected, long fileSize) {
        String s = FileUtils.readableByteSize(fileSize);
        assertEquals(expected, s, "Different file size string");
    }

    /** Replace Runtime.exec with ProcessBuilder */
    private static List<Path> dfCommand() throws IOException {
        final Process p = new ProcessBuilder("df", "-a").start();
        final Pattern dfPattern = Pattern.compile("^(\\/[^ ]+)[^/]+(/.*)$");

        System.out.println("df -a");
        try (var br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            return br.lines() //
                    .peek(System.out::println) // Debug
                    .map(dfPattern::matcher) //
                    .filter(Matcher::matches) // Mounted devices only (without Fuse, Loopback...)
                    .filter(m -> FileUtils.SD_MOUNT_PATTERN.matcher(m.group(1)).matches()) //
                    .map(m -> Path.of(m.group(2))) //
                    .collect(Collectors.toList());
        }
    }
}