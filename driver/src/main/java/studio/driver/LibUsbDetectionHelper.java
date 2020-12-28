/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver;

import org.usb4java.Context;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;
import studio.driver.event.DeviceHotplugEventListener;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LibUsbDetectionHelper {

    private static final Logger LOGGER = Logger.getLogger(LibUsbDetectionHelper.class.getName());

    // USB device
    public static final int VENDOR_ID_FW1 = 0x0c45;
    public static final int PRODUCT_ID_FW1 = 0x6820;
    public static final int VENDOR_ID_FW2 = 0x0c45;
    public static final int PRODUCT_ID_FW2 = 0x6840;
    public static final int VENDOR_ID_V2 = 0x0483;
    public static final int PRODUCT_ID_V2 = 0xa341;

    private static final long POLL_DELAY = 5000L;

    // LibUsb context
    private static Context context = new Context();
    // Worker thread to handle libusb async events
    private static LibUsbAsyncEventsWorker asyncEventHandlerWorker = null;
    // Scheduled task to actively poll device when hotplug is not supported
    private static ScheduledExecutorService scheduledExecutor = null;
    private static Future<?> activePollingTask = null;

    /**
     * Initialize libusb context, start async event handling worker thread, register hotplug listener, and handle
     * de-initialization on JVM shutdown.
     * @param deviceVersion The version of the device to detect
     * @param listener A hotplug listener
     */
    public static void initializeLibUsb(DeviceVersion deviceVersion, DeviceHotplugEventListener listener) {
        // Init libusb
        LOGGER.info("Initializing libusb...");
        context = new Context();
        int result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS) {
            throw new StoryTellerException("Unable to initialize libusb.", new LibUsbException(result));
        }

        // Enable libusb debug logs
        //LibUsb.setOption(context, LibUsb.OPTION_LOG_LEVEL, LibUsb.LOG_LEVEL_DEBUG);

        // Start worker thread to handle libusb async events
        asyncEventHandlerWorker = new LibUsbAsyncEventsWorker(context);
        asyncEventHandlerWorker.start();

        // Hotplug detection
        if (LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
            LOGGER.info("Hotplug is supported. Registering hotplug callback(s)...");
            if (deviceVersion == DeviceVersion.DEVICE_VERSION_1 || deviceVersion == DeviceVersion.DEVICE_VERSION_ANY) {
                registerCallback(VENDOR_ID_FW1, PRODUCT_ID_FW1, listener);
            }
            if (deviceVersion == DeviceVersion.DEVICE_VERSION_2 || deviceVersion == DeviceVersion.DEVICE_VERSION_ANY) {
                registerCallback(VENDOR_ID_FW2, PRODUCT_ID_FW2, listener);
                registerCallback(VENDOR_ID_V2, PRODUCT_ID_V2, listener);
            }
        } else {
            LOGGER.info("Hotplug is NOT supported. Scheduling task to actively poll USB device...");
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            activePollingTask = scheduledExecutor.scheduleAtFixedRate(
                    new LibUsbActivePollingWorker(context, deviceVersion, listener),
                    0, POLL_DELAY, TimeUnit.MILLISECONDS);
        }

        // De-initialize libusb context  and stop worker threads when JVM exits
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    if (activePollingTask != null && !activePollingTask.isDone()) {
                        LOGGER.info("Stopping active polling worker task");
                        activePollingTask.cancel(true);
                    }
                    if (scheduledExecutor != null) {
                        LOGGER.info("Shutting down active polling executor");
                        scheduledExecutor.shutdown();
                    }
                    if (asyncEventHandlerWorker != null) {
                        LOGGER.info("Stopping async event handling worker thread");
                        asyncEventHandlerWorker.abort();
                        try {
                            asyncEventHandlerWorker.join();
                        } catch (InterruptedException e) {
                            LOGGER.log(Level.SEVERE, "Failed to stop async event handling worker thread", e);
                        }
                    }
                    LOGGER.info("Exiting libusb...");
                    LibUsb.exit(context);
                })
        );
    }

    private static void registerCallback(int vendorId, int productId, DeviceHotplugEventListener listener) {
        LibUsb.hotplugRegisterCallback(
                context,
                LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED | LibUsb.HOTPLUG_EVENT_DEVICE_LEFT,
                LibUsb.HOTPLUG_ENUMERATE,   // Arm the callback and fire it for all matching currently attached devices
                vendorId,
                productId,
                LibUsb.HOTPLUG_MATCH_ANY,   // Device class
                (ctx, device, event, userData) -> {
                    LOGGER.info(String.format("Hotplug event callback (%04x:%04x): " + event, vendorId, productId));
                    switch (event) {
                        case LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED:
                            CompletableFuture.runAsync(() -> listener.onDevicePlugged(device));
                            break;
                        case LibUsb.HOTPLUG_EVENT_DEVICE_LEFT:
                            CompletableFuture.runAsync(() -> listener.onDeviceUnplugged(device));
                            break;
                    }
                    return 0;   // Do not deregister the callback
                },
                null,
                null
        );
    }
}
