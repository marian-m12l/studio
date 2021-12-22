/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SystemUtils;

import studio.driver.StoryTellerException;

public class DeviceUtils {

    @Deprecated
    private static final Logger LOGGER = Logger.getLogger(DeviceUtils.class.getName());

    /** Simple OS test. */
    public static boolean isWindows() {
        return '/' != File.separatorChar;
    }

    /** List root mount points. */
    public static List<String> listMountPoints() {
        List<String> l = new ArrayList<>();
        FileSystem fs = FileSystems.getDefault();
        // Windows
        if (isWindows()) {
            for (Path p : fs.getRootDirectories()) {
                l.add(p.toString());
            }
            return l;
        }
        // Unix & Mac
        for (FileStore f : fs.getFileStores()) {
            // Mounted devices only (without Fuse)
            if (f.name().startsWith("/dev/s")) {
                // find mount path in toString()
                l.add(f.toString().split(" ")[0]);
            }
        }
        return l;
    }

    @Deprecated
    public static List<String> listMountPoints0() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return Arrays.stream(File.listRoots()) //
                    .map(root -> root.toPath().toString()) //
                    .collect(Collectors.toList());
        } else {
            final String CMD_DF = "df -l";
            final Pattern dfPattern = Pattern.compile("^(\\/[^ ]+)[^/]+(/.*)$");

            List<String> mountPoints = new ArrayList<>();

            Process dfProcess = null;
            BufferedReader dfReader = null;
            try {
                dfProcess = Runtime.getRuntime().exec(CMD_DF);
                dfReader = new BufferedReader(new InputStreamReader(dfProcess.getInputStream()));
                String dfLine;
                while ((dfLine = dfReader.readLine()) != null) {
                    if (dfLine.isEmpty()) {
                        continue;
                    }
                    final Matcher matcher = dfPattern.matcher(dfLine);
                    if (matcher.matches()) {
                        final String dev = matcher.group(1);
                        final String rootPath = matcher.group(2);
                        if (dev.startsWith("/dev/")) {
                            mountPoints.add(rootPath);
                        }
                    }
                }
            } catch (IOException e) {
                throw new StoryTellerException("Failed to list mount points", e);
            } finally {
                if (dfProcess != null) {
                    try {
                        int exitValue = dfProcess.waitFor();
                        if (exitValue != 0) {
                            LOGGER.log(Level.SEVERE, "Abnormal command termination. Exit value: " + exitValue);
                        }
                        dfProcess.destroy();
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.SEVERE, "Error while waiting for command to complete", e);
                    }
                }
                if (dfReader != null) {
                    try {
                        dfReader.close();
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "Failed to close command output reader", e);
                    }
                }
            }

            return mountPoints;
        }
    }

}
