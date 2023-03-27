/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.driver.model;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public final class DeviceInfosDTO {
    private UUID uuid; // driver v1 only
    private byte[] deviceKey; // driver v2 only
    private String serial;
    private String firmware;
    private String driver; // PackFormat
    private boolean error;
    private boolean plugged;
    private StorageDTO storage;

    public void setFirmware(short major, short minor) {
        firmware = major + "." + minor;
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public static final class StorageDTO {
        private long size;
        private long free;
        private long taken;

        public void updateFree() {
            free = size - taken;
        }
    }
}