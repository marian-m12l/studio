package io.quarkus.awt.runtime.graal;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

@Platforms({ Platform.WINDOWS_AMD64.class, Platform.WINDOWS_AARCH64.class })
public class WindowsAwtFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // Quarkus run time init for AWT in Windows
        RuntimeClassInitialization.initializeAtRunTime("sun.awt.windows");
    }
}