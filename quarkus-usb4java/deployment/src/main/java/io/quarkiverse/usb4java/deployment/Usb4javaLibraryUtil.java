package io.quarkiverse.usb4java.deployment;

import org.usb4java.LoaderException;

/**
 * This code is extracted from the {@link Loader} class as most things are
 * private there.
 */
public class Usb4javaLibraryUtil {

    private Usb4javaLibraryUtil() {
        // empty
    }

    public static String extractLibrary() {
        final String platform = getPlatform();
        final String lib = getLibName();
        return extractLibrary(platform, lib);
    }

    public static String extractLibrary(final String platform,
            final String lib) {
        // Extract the usb4java library
        return // '/' +
        "org/usb4java/" + platform + "/" + lib;
    }

    /**
     * Returns the platform name. This could be for example "linux-x86" or
     * "windows-x86_64".
     *
     * @return The architecture name. Never null.
     */
    public static String getPlatform() {
        return getOS() + "-" + getArch();
    }

    /**
     * Returns the name of the usb4java native library. This could be
     * "libusb4java.dll" for example.
     *
     * @return The usb4java native library name. Never null.
     */
    public static String getLibName() {
        return "libusb4java." + getExt();
    }

    /**
     * Returns the operating system name. This could be "linux", "windows" or
     * "macos" or (for any other non-supported platform) the value of the
     * "os.name" property converted to lower case and with removed space
     * characters.
     *
     * @return The operating system name.
     */
    private static String getOS() {
        final String os = System.getProperty("os.name").toLowerCase()
                .replace(" ", "");
        if (os.contains("windows")) {
            return "win32";
        }
        if (os.equals("macosx") || os.equals("macos")) {
            return "darwin";
        }
        return os;
    }

    /**
     * Returns the CPU architecture. This will be "x86" or "x86_64" (Platform
     * names i386 und amd64 are converted accordingly) or (when platform is
     * unsupported) the value of os.arch converted to lower-case and with
     * removed space characters.
     *
     * @return The CPU architecture
     */
    private static String getArch() {
        final String arch = System.getProperty("os.arch").toLowerCase()
                .replace(" ", "");
        if (arch.equals("i386")) {
            return "x86";
        }
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86-64";
        }
        if (arch.equals("arm64")) {
            return "aarch64";
        }
        if (arch.equals("armhf") || arch.equals("aarch32") || arch.equals("armv7l")) {
            return "arm";
        }
        return arch;
    }

    /**
     * Returns the shared library extension name.
     *
     * @return The shared library extension name.
     */
    private static String getExt() {
        final String os = getOS();
        final String key = "usb4java.libext." + getOS();
        final String ext = System.getProperty(key);
        if (ext != null) {
            return ext;
        }
        if (os.equals("linux") || os.equals("freebsd") || os.equals("sunos")) {
            return "so";
        }
        if (os.equals("win32")) {
            return "dll";
        }
        if (os.equals("darwin")) {
            return "dylib";
        }
        throw new LoaderException("Unable to determine the shared library "
                + "file extension for operating system '" + os
                + "'. Please specify Java parameter -D" + key
                + "=<FILE-EXTENSION>");
    }
}
