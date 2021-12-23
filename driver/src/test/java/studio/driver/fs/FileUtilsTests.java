package studio.driver.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilsTests {

    @TempDir
    Path tmp;

    private static String CONTENT = "Hello World";

    @Test
    void testFileSize() throws IOException {
        Path f1 = addFile(tmp, "f1.txt");
        long fileSize = FileUtils.getFileSize(f1);
        System.out.printf("File size %s : %s bytes \n", f1, fileSize);
        assertEquals(CONTENT.length(), fileSize, "Different file size");
    }

    @Test
    void testFolderSize() throws IOException {
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
}