/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.usb4java.Context;
import org.usb4java.DeviceDescriptor;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import studio.core.v1.utils.exception.StoryTellerException;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;

public class LibUsbDetectionHelper {

    private static final Logger LOGGER = LogManager.getLogger(LibUsbDetectionHelper.class);

    // USB device
    private static final int VENDOR_ID_FW1 = 0x0c45;
    private static final int PRODUCT_ID_FW1 = 0x6820;
    private static final int VENDOR_ID_FW2 = 0x0c45;
    private static final int PRODUCT_ID_FW2 = 0x6840;
    private static final int VENDOR_ID_V2 = 0x0483;
    private static final int PRODUCT_ID_V2 = 0xa341;

    private static final long POLL_DELAY = 5000L;

    // Scheduled task to actively poll device when hotplug is not supported
    private static ScheduledExecutorService scheduledExecutor = null;
    private static Future<?> activePollingTask = null;

    private LibUsbDetectionHelper() {
        throw new IllegalArgumentException("Utility class");
    }

    /**
     * Initialize libusb context, start async event handling worker thread, register
     * hotplug listener, and handle de-initialization on JVM shutdown.
     * 
     * @param deviceVersion The version of the device to detect
     * @param listener      A hotplug listener
     */
    public static void initializeLibUsb(DeviceVersion deviceVersion, DevicePluggedListener pluggedlistener,
            DeviceUnpluggedListener unpluggedlistener) {
        // Init libusb
        LOGGER.info("Initializing libusb...");
        // LibUsb context
        Context context = new Context();
        int result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS) {
            throw new StoryTellerException("Unable to initialize libusb.", new LibUsbException(result));
        }

        // Enable libusb debug logs
        // Uncomment for trace : LibUsb.setOption(context, LibUsb.OPTION_LOG_LEVEL, LibUsb.LOG_LEVEL_DEBUG)

        // Worker thread to handle libusb async events
        LibUsbAsyncEventsWorker asyncEventHandlerWorker = new LibUsbAsyncEventsWorker(context);
        // Start worker thread to handle libusb async events
        asyncEventHandlerWorker.start();

        // Hotplug detection
        if (LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
            LOGGER.info("Hotplug is supported. Registering hotplug callback(s)...");
            if (isDeviceV1(deviceVersion)) {
                registerCallback(context, VENDOR_ID_FW1, PRODUCT_ID_FW1, pluggedlistener, unpluggedlistener);
            }
            if (isDeviceV2(deviceVersion)) {
                registerCallback(context, VENDOR_ID_FW2, PRODUCT_ID_FW2, pluggedlistener, unpluggedlistener);
                registerCallback(context, VENDOR_ID_V2, PRODUCT_ID_V2, pluggedlistener, unpluggedlistener);
            }
        } else {
            LOGGER.info("Hotplug is NOT supported. Scheduling task to actively poll USB device...");
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            activePollingTask = scheduledExecutor.scheduleAtFixedRate(
                    new LibUsbActivePollingWorker(context, deviceVersion, pluggedlistener, unpluggedlistener), 0, POLL_DELAY,
                    TimeUnit.MILLISECONDS);
        }
        // Add shutdown hook
        registerHook(context, asyncEventHandlerWorker);
    }

    /**
     * De-initialize libusb context and stop worker threads when JVM exits
     * @param asyncEventHandlerWorker
     */
    private static void registerHook(Context context, LibUsbAsyncEventsWorker asyncEventHandlerWorker) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
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
                    LOGGER.error("Failed to stop async event handling worker thread", e);
                    Thread.currentThread().interrupt();
                }
            }
            LOGGER.info("Exiting libusb...");
            LibUsb.exit(context);
        }));
    }
    
    private static void registerCallback(Context context, int vendorId, int productId,
            DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener) {
        LibUsb.hotplugRegisterCallback(context, LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED | LibUsb.HOTPLUG_EVENT_DEVICE_LEFT,
                LibUsb.HOTPLUG_ENUMERATE, // Arm the callback and fire it for all matching currently attached devices
                vendorId, productId, LibUsb.HOTPLUG_MATCH_ANY, // Device class
                (ctx, device, event, userData) -> {
                    String msg = String.format("Hotplug event callback (%04x:%04x): %s", vendorId, productId, event);
                    LOGGER.info(msg);
                    if(event ==  LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED ) {
                        CompletableFuture.runAsync(() -> pluggedlistener.onDevicePlugged(device)).exceptionally(e -> {
                            LOGGER.error("An error occurred while handling device plug event", e);
                            return null;
                        });
                    } else if(event == LibUsb.HOTPLUG_EVENT_DEVICE_LEFT ) {
                        CompletableFuture.runAsync(() -> unpluggedlistener.onDeviceUnplugged(device)).exceptionally(e -> {
                            LOGGER.error("An error occurred while handling device unplug event", e);
                            return null;
                        });
                    }
                    return 0; // Do not deregister the callback
                }, null, null);
    }

    /** Check device version.*/
    private static boolean isDevice(DeviceVersion actual, DeviceVersion expected) {
        return actual == expected || actual == DeviceVersion.DEVICE_VERSION_ANY;
    }

    public static boolean isDeviceV1(DeviceVersion actual) {
        return isDevice(actual, DeviceVersion.DEVICE_VERSION_1);
    }

    public static boolean isDeviceV2(DeviceVersion actual) {
        return isDevice(actual, DeviceVersion.DEVICE_VERSION_2);
    }

    /** Check firmware version.*/
    private static boolean isFirmware(DeviceDescriptor desc, int vendorId, int productId) {
        return desc.idVendor() == vendorId && desc.idProduct() == productId;
    }

    public static boolean isFirmwareV1(DeviceDescriptor desc) {
        return isFirmware(desc, VENDOR_ID_FW1, PRODUCT_ID_FW1);
    }

    public static boolean isFirmwareV2(DeviceDescriptor desc) {
        return isFirmware(desc, VENDOR_ID_FW2, PRODUCT_ID_FW2)
                || isFirmware(desc, VENDOR_ID_V2, (short) (PRODUCT_ID_V2 & 0xffff));
    }

}
