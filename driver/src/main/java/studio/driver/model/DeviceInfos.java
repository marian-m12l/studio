/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model;

import java.util.UUID;

public class DeviceInfos {

    private UUID uuid;
    private short firmwareMajor, firmwareMinor;
    private String serialNumber;
    private int sdCardSizeInSectors;
    private int usedSpaceInSectors;
    private boolean inError;

    public DeviceInfos() {
    }

    public DeviceInfos(UUID uuid, short firmwareMajor, short firmwareMinor, String serialNumber, int sdCardSizeInSectors, int usedSpaceInSectors, boolean inError) {
        this.uuid = uuid;
        this.firmwareMajor = firmwareMajor;
        this.firmwareMinor = firmwareMinor;
        this.serialNumber = serialNumber;
        this.sdCardSizeInSectors = sdCardSizeInSectors;
        this.usedSpaceInSectors = usedSpaceInSectors;
        this.inError = inError;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public short getFirmwareMajor() {
        return firmwareMajor;
    }

    public void setFirmwareMajor(short firmwareMajor) {
        this.firmwareMajor = firmwareMajor;
    }

    public short getFirmwareMinor() {
        return firmwareMinor;
    }

    public void setFirmwareMinor(short firmwareMinor) {
        this.firmwareMinor = firmwareMinor;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
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
        return "DeviceInfos{" +
                "uuid=" + uuid +
                ", firmwareMajor=" + firmwareMajor +
                ", firmwareMinor=" + firmwareMinor +
                ", serialNumber='" + serialNumber + '\'' +
                ", sdCardSizeInSectors=" + sdCardSizeInSectors +
                ", usedSpaceInSectors=" + usedSpaceInSectors +
                ", inError=" + inError +
                '}';
    }
}
