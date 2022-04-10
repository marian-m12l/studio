/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DeviceUtils {

    private DeviceUtils() {
        throw new IllegalArgumentException("Utility class");
    }

    /** Simple OS test. */
    public static boolean isWindows() {
        return '/' != File.separatorChar;
    }

    /** List root mount points. */
    public static List<Path> listMountPoints() {
        List<Path> l = new ArrayList<>();
        FileSystem fs = FileSystems.getDefault();
        // Windows
        if (isWindows()) {
            for (Path p : fs.getRootDirectories()) {
                l.add(p);
            }
            return l;
        }
        // Unix & Mac
        for (FileStore f : fs.getFileStores()) {
            // Mounted devices only (without Fuse, Loopback...)
            if (f.name().startsWith("/dev/s") || f.name().startsWith("/dev/disk")) {
                // find mount path in toString()
                l.add(Path.of(f.toString().split(" ")[0]));
            }
        }
        return l;
    }

    /** Read little endian short from stream. */
    public static short readLittleEndianShort(InputStream is) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(is.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN);
        return bb.getShort();
    }

    /** Read big endian long from stream. */
    public static long readBigEndianLong(InputStream is) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(is.readNBytes(8)).order(ByteOrder.BIG_ENDIAN);
        return bb.getLong();
    }

}
