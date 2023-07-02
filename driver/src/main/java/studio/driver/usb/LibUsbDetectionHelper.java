/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.driver.usb;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.usb4java.Context;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.core.v1.exception.StoryTellerException;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.model.UsbDeviceFirmware;
import studio.driver.model.UsbDeviceVersion;

public enum LibUsbDetectionHelper {

    INSTANCE;

    private final Logger LOGGER = LoggerFactory.getLogger(LibUsbDetectionHelper.class);

    private static final long POLL_DELAY = 5L;

    // Scheduled task to actively poll device when hotplug is not supported
    private ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pollTask;

    // LibUsb context
    private Context context;

    /**
     * Initialize libusb context, start async event handling worker thread, register
     * hotplug listener, and handle de-initialization on JVM shutdown.
     *
     * @param deviceVersion The version of the device to detect
     * @param listener      A hotplug listener
     */
    private LibUsbDetectionHelper() {
        // Init libusb
        LOGGER.info("Initializing libusb...");
        // LibUsb context
        context = new Context();
        int result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS) {
            throw new StoryTellerException("Unable to initialize libusb.", new LibUsbException(result));
        }
        // Enable libusb debug logs
        if(LOGGER.isTraceEnabled()) {
            LibUsb.setOption(context, LibUsb.OPTION_LOG_LEVEL, LibUsb.LOG_LEVEL_DEBUG);
        }
        // Daemon thread to handle libusb async events
        var asyncEvtWorker = new LibUsbAsyncEventsWorker(context);
        asyncEvtWorker.setDaemon(true);
        asyncEvtWorker.start();
        // Shutdown hook
        registerHook(asyncEvtWorker);
    }

    public void initializeLibUsb(UsbDeviceVersion deviceVersion, DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener) {
        // Hotplug detection
        if (LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
            LOGGER.info("Hotplug is supported. Registering hotplug callback(s)...");
            if (deviceVersion.isV1()) {
                registerCallback(UsbDeviceFirmware.FW1, pluggedlistener, unpluggedlistener);
            }
            if (deviceVersion.isV2()) {
                registerCallback(UsbDeviceFirmware.FW2, pluggedlistener, unpluggedlistener);
                registerCallback(UsbDeviceFirmware.V2, pluggedlistener, unpluggedlistener);
            }
        } else {
            LOGGER.info("Hotplug is NOT supported. Scheduling task to actively poll USB device...");
            Runnable r = new LibUsbActivePollingWorker(context, deviceVersion, pluggedlistener, unpluggedlistener);
            pollTask = scheduledExecutor.scheduleAtFixedRate(r, 0, POLL_DELAY, TimeUnit.SECONDS);
        }
    }

    /**
     * De-initialize libusb context and stop worker threads when JVM exits.
     * @param asyncEvtWorker
     */
    private void registerHook(LibUsbAsyncEventsWorker asyncEvtWorker) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (pollTask != null && !pollTask.isDone()) {
                LOGGER.info("Stopping active polling worker task");
                pollTask.cancel(true);
            }
            if (scheduledExecutor != null) {
                LOGGER.info("Shutting down active polling executor");
                scheduledExecutor.shutdown();
            }
            if (asyncEvtWorker != null) {
                LOGGER.info("Stopping async event handling worker thread");
                asyncEvtWorker.abort();
            }
            LOGGER.info("Exiting libusb...");
            // TODO: LibUsb.exit(context);
        }));
    }

    private void registerCallback(UsbDeviceFirmware df, DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener) {
        LibUsb.hotplugRegisterCallback(context, LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED | LibUsb.HOTPLUG_EVENT_DEVICE_LEFT,
                LibUsb.HOTPLUG_ENUMERATE, // Arm the callback and fire it for all matching currently attached devices
                df.getVendorId(), df.getProductId(), LibUsb.HOTPLUG_MATCH_ANY, // Device class
                (ctx, device, event, userData) -> {
                    LOGGER.info("Hotplug event callback ({}:{}): {}", df.getVendorId(), df.getProductId(), event);
                    if (event == LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED) {
                        CompletableFuture.runAsync(() -> pluggedlistener.onDevicePlugged(device));
                    } else if (event == LibUsb.HOTPLUG_EVENT_DEVICE_LEFT) {
                        CompletableFuture.runAsync(() -> unpluggedlistener.onDeviceUnplugged(device));
                    }
                    return 0; // Do not deregister the callback
                },  null, null);
    }
}
