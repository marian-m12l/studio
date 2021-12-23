/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class FileUtils {

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

    public static void deleteDirectory(Path path) throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()) //
                    .map(Path::toFile) //
                    .forEach(File::delete);
        }
    }

}
