/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.core.v1.utils.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.model.TransferListener.TransferProgressListener;
import studio.core.v1.model.TransferListener.TransferStatus;
import studio.core.v1.utils.stream.ThrowingConsumer;

public class FileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    // Mounted devices only (without Fuse, Loopback...)
    protected static final Pattern SD_MOUNT_PATTERN = Pattern.compile("^(/dev/s|/dev/disk/|msdos).*$");

    private FileUtils() {
        throw new IllegalArgumentException("Utility class");
    }

    /** Simple OS test. */
    public static boolean isWindows() {
        return '/' != File.separatorChar;
    }

    /** List root mount points. */
    public static List<Path> listMountPoints() {
        FileSystem fs = FileSystems.getDefault();
        List<Path> l = new ArrayList<>();
        // Windows
        if (isWindows()) {
            for (Path p : fs.getRootDirectories()) {
                l.add(p);
            }
            return l;
        }
        // Unix & Mac
        for (FileStore f : fs.getFileStores()) {
            if(SD_MOUNT_PATTERN.matcher(f.name()).matches()) {
                // find mount path in toString()
                l.add(Path.of(f.toString().split(" ")[0]));
            }
        }
        return l;
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

    public static long getFileSize(Path path) throws IOException {
        return Files.size(path);
    }

    public static DataInputStream dataInputStream(Path path) throws IOException {
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
    }

    public static DataOutputStream dataOutputStream(Path path) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
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

    public static String readablePercent(long ratio, long total) {
        return readablePercent(100.0 * ratio / total);
    }

    public static String readablePercent(double value) {
        return "" + Math.round(100.0 * 100.0 * value) / 100.0 + "%";
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

    public static TransferStatus copyFolder(UUID uuid, Path sourceFolder, Path destFolder, TransferProgressListener listener) throws IOException {
        long folderSize = getFolderSize(sourceFolder);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Pack folder size: {}", readableByteSize(folderSize));
        }
        TransferStatus status = new TransferStatus(uuid, folderSize);
        // Target directory
        Files.createDirectories(destFolder);
        // Copy folders and files
        try (Stream<Path> paths = Files.walk(sourceFolder)) {
            paths.forEach(ThrowingConsumer.unchecked(s -> {
                Path d = destFolder.resolve(sourceFolder.relativize(s));
                // Copy directory
                if (Files.isDirectory(s)) {
                    LOGGER.debug("Creating directory {}", d);
                    Files.createDirectories(d);
                } else {
                    // Copy files
                    long fileSize = getFileSize(s);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Copying file {} ({}) to {}", s.getFileName(), readableByteSize(fileSize), d);
                    }
                    Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                    // Compute progress and speed
                    status.update(fileSize);
                    // Call listener with transfer status
                    if (listener != null) {
                        listener.onProgress(status);
                    }
                }
            }));
        }
        return status;
    }
}
