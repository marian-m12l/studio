/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DeviceUtils {

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
            if (f.name().startsWith("/dev/s")) {
                // find mount path in toString()
                l.add(Path.of(f.toString().split(" ")[0]));
            }
        }
        return l;
    }

}
