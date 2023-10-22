package io.quarkiverse.usb4java.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.usb4java.Loader;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class NativeUsb4javaIT {

    @Test
    void testUsbLoader() {
        Assertions.assertDoesNotThrow(() -> Loader.load());
    }
}
