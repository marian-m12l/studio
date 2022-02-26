/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model.raw;

import java.util.UUID;

import studio.driver.model.DeviceInfos;

public class RawDeviceInfos extends DeviceInfos {

    private UUID uuid;
    private int sdCardSizeInSectors;
    private int usedSpaceInSectors;
    private boolean inError;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public int getSdCardSizeInSectors() {
        return sdCardSizeInSectors;
    }

    public void setSdCardSizeInSectors(int sdCardSizeInSectors) {
        this.sdCardSizeInSectors = sdCardSizeInSectors;
    }

    public int getUsedSpaceInSectors() {
        return usedSpaceInSectors;
    }

    public void setUsedSpaceInSectors(int usedSpaceInSectors) {
        this.usedSpaceInSectors = usedSpaceInSectors;
    }

    public boolean isInError() {
        return inError;
    }

    public void setInError(boolean inError) {
        this.inError = inError;
    }

    @Override
    public String toString() {
        return "RawDeviceInfos{" + "uuid=" + uuid + ", firmwareMajor=" + getFirmwareMajor() + ", firmwareMinor="
                + getFirmwareMinor() + ", serialNumber='" + getSerialNumber() + '\'' + ", sdCardSizeInSectors="
                + sdCardSizeInSectors + ", usedSpaceInSectors=" + usedSpaceInSectors + ", inError=" + inError + '}';
    }
}
