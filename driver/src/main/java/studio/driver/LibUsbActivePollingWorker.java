/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver;

import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import studio.driver.event.DeviceHotplugEventListener;

public class LibUsbActivePollingWorker implements Runnable {

    private static final Logger LOGGER = LogManager.getLogger(LibUsbActivePollingWorker.class);

    private final Context context;
    private final DeviceVersion deviceVersion;
    private final DeviceHotplugEventListener listener;
    private Device device = null;

    public LibUsbActivePollingWorker(Context context, DeviceVersion deviceVersion,
            DeviceHotplugEventListener listener) {
        this.context = context;
        this.deviceVersion = deviceVersion;
        this.listener = listener;
    }

    @Override
    public void run() {
        // List available devices
        DeviceList devices = new DeviceList();
        int result = LibUsb.getDeviceList(context, devices);
        if (result < 0) {
            throw new LibUsbException("Unable to get libusb device list", result);
        }
        try {
            // Iterate over all devices and scan for the right one
            Device found = null;
            for (Device d : devices) {
                DeviceDescriptor desc = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(d, desc);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("Unable to read libusb device descriptor", result);
                }
                if ((LibUsbDetectionHelper.isDeviceV1(deviceVersion) && LibUsbDetectionHelper.isFirmwareV1(desc)) || //
                        (LibUsbDetectionHelper.isDeviceV2(deviceVersion) && LibUsbDetectionHelper.isFirmwareV2(desc))) {
                    found = d;
                    break;
                }
            }
            // Fire plugged / unplugged events
            if (found != null && device == null) {
                LOGGER.info("Active polling found a new device. Firing event.");
                device = found;
                CompletableFuture.runAsync(() -> listener.onDevicePlugged(device));
            } else if (found == null && device != null) {
                LOGGER.info("Active polling lost the device. Firing event.");
                CompletableFuture.runAsync(() -> listener.onDeviceUnplugged(device));
                device = null;
            }
        } finally {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(devices, false); // Do NOT unref devices
        }
    }

}
