package io.quarkiverse.usb4java.deployment;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class NativeUsb4javaIT {// extends Usb4javaTest {

    @Test
    void testBasic() {
        System.out.println("Started");
    }
}