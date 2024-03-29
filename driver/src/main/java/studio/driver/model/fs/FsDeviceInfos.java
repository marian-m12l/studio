/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model.fs;

import org.apache.commons.codec.binary.Hex;

public class FsDeviceInfos {

    private byte[] uuid;
    private short firmwareMajor, firmwareMinor;
    private String serialNumber;
    private long sdCardSizeInBytes;
    private long usedSpaceInBytes;
    private FsDeviceKeyV3 deviceKeyV3;

    public FsDeviceInfos() {
    }

    public FsDeviceInfos(byte[] uuid, short firmwareMajor, short firmwareMinor, String serialNumber, long sdCardSizeInBytes, long usedSpaceInBytes) {
        this.uuid = uuid;
        this.firmwareMajor = firmwareMajor;
        this.firmwareMinor = firmwareMinor;
        this.serialNumber = serialNumber;
        this.sdCardSizeInBytes = sdCardSizeInBytes;
        this.usedSpaceInBytes = usedSpaceInBytes;
    }

    public byte[] getUuid() {
        return uuid;
    }

    public void setUuid(byte[] uuid) {
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

    public FsDeviceKeyV3 getDeviceKeyV3() {
        return deviceKeyV3;
    }

    public void setDeviceKeyV3(FsDeviceKeyV3 deviceKeyV3) {
        this.deviceKeyV3 = deviceKeyV3;
    }

    public long getSdCardSizeInBytes() {
        return sdCardSizeInBytes;
    }

    public void setSdCardSizeInBytes(long sdCardSizeInBytes) {
        this.sdCardSizeInBytes = sdCardSizeInBytes;
    }

    public long getUsedSpaceInBytes() {
        return usedSpaceInBytes;
    }

    public void setUsedSpaceInBytes(long usedSpaceInBytes) {
        this.usedSpaceInBytes = usedSpaceInBytes;
    }

    @Override
    public String toString() {
        return "FsDeviceInfos{" +
                "uuid=" + Hex.encodeHexString(uuid) +
                ", firmwareMajor=" + firmwareMajor +
                ", firmwareMinor=" + firmwareMinor +
                ", serialNumber='" + serialNumber + '\'' +
                ", sdCardSizeInBytes=" + sdCardSizeInBytes +
                ", usedSpaceInBytes=" + usedSpaceInBytes +
                ", deviceKeyV3=" + deviceKeyV3.toString() +
                '}';
    }
}
