/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver;

import org.usb4java.Context;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import java.util.logging.Logger;

public class LibUsbAsyncEventsWorker extends Thread {

    private static final Logger LOGGER = Logger.getLogger(LibUsbAsyncEventsWorker.class.getName());

    private volatile boolean abort;
    private Context context;

    public LibUsbAsyncEventsWorker(Context context) {
        this.context = context;
    }

    public void abort() {
        this.abort = true;
    }

    @Override
    public void run() {
        LOGGER.fine("Starting worker thread to handle libusb async events...");
        while (!this.abort) {
            int result = LibUsb.handleEventsTimeout(this.context, 250000);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to handle libusb async events", result);
            }
        }
    }
}
