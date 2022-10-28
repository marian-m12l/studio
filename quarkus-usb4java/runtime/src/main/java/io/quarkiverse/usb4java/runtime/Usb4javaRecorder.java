package io.quarkiverse.usb4java.runtime;

import org.usb4java.Loader;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class Usb4javaRecorder {

    public void loadUsb4javaLibrary() {
        Loader.load();
    }
}
