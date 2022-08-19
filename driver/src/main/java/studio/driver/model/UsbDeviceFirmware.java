/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model;

import org.usb4java.DeviceDescriptor;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UsbDeviceFirmware {

    FW1((short) 0x0c45, (short) 0x6820), FW2((short) 0x0c45, (short) 0x6840), V2((short) 0x0483, (short) 0xa341);

    private final short vendorId;
    private final short productId;

    private static boolean isFirmware(UsbDeviceFirmware df, DeviceDescriptor desc) {
        return desc.idVendor() == df.vendorId && desc.idProduct() == df.productId;
    }

    public static boolean isV1(DeviceDescriptor desc) {
        return isFirmware(FW1, desc);
    }

    public static boolean isV2(DeviceDescriptor desc) {
        return isFirmware(FW2, desc) || isFirmware(V2, desc);
    }
}
