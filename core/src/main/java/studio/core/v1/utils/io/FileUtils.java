/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import studio.core.v1.exception.StoryTellerException;

public class FileUtils {

    private FileUtils() {
        throw new IllegalArgumentException("Utility class");
    }

    public static void deleteDirectory(Path path) throws IOException {
        // already deleted
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()) //
                    .map(Path::toFile) //
                    .forEach(File::delete);
        }
    }

    public static void emptyDirectory(Path path) throws IOException {
        deleteDirectory(path);
        Files.createDirectories(path);
    }

    public static long getFolderSize(Path path) throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            return paths.map(Path::toFile) //
                    .filter(File::isFile) //
                    .mapToLong(File::length) //
                    .sum();
        }
    }

    // deprecated?
    public static long getFileSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Return human readable file size. This function is limited to exabyte.
     * 
     * @param size
     * @return a human-readable display value (includes units - EB, PB, TB, GB, MB,
     *         KB or bytes).
     */
    public static String readableByteSize(long byteSize) {
        if (byteSize < 1024) {
            return byteSize + " bytes";
        }
        int exp = (int) (Math.log10(byteSize) / Math.log10(1024d));
        double conv = byteSize / Math.pow(1024d, exp);
        double trunc = Math.round(conv * 100d) / 100d;
        char unit = "KMGTPE".charAt(exp - 1);
        return "" + trunc + " " + unit + "B";
    }

    /**
     * Create directories. Throw custom unchecked StoryTellerException.
     * 
     * @param errorMessage
     * @param dirPath
     */
    public static void createDirectories(String errorMessage, Path dirPath) {
        try {
            if (!Files.isDirectory(dirPath)) {
                Files.createDirectories(dirPath);
            }
        } catch (IOException e) {
            throw new StoryTellerException(errorMessage, e);
        }
    }
}
