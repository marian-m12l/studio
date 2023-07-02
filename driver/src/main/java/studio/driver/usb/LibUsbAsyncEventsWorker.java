/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.usb;

import org.usb4java.Context;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class LibUsbAsyncEventsWorker extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibUsbAsyncEventsWorker.class);

    private final Context context;
    private boolean abort;

    public void abort() {
        abort = true;
    }

    @Override
    public void run() {
        LOGGER.debug("Starting worker thread to handle libusb async events...");
        while (!abort) {
            int result = LibUsb.handleEventsTimeout(context, 250_000);
            if (!abort && result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to handle libusb async events", result);
            }
        }
    }
}
