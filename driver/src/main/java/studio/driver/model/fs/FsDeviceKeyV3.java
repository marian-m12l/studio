/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model.fs;

import java.util.Arrays;

public class FsDeviceKeyV3 {

    private byte[] aesKey;
    private byte[] aesIv;
    private byte[] bt;

    public FsDeviceKeyV3() {
    }

    public FsDeviceKeyV3(byte[] aesKey, byte[] aesIv, byte[] bt) {
        this.aesKey = aesKey;
        this.aesIv = aesIv;
        this.bt = bt;
    }

    public byte[] getAesKey() {
        return aesKey;
    }

    public void setAesKey(byte[] aesKey) {
        this.aesKey = aesKey;
    }

    public byte[] getAesIv() {
        return aesIv;
    }

    public void setAesIv(byte[] aesIv) {
        this.aesIv = aesIv;
    }

    public byte[] getBt() {
        return bt;
    }

    public void setBt(byte[] bt) {
        this.bt = bt;
    }

    @Override
    public String toString() {
        return "FsDeviceKeyV3{" +
                "aesKey=" + Arrays.toString(aesKey) +
                ", aesIv=" + Arrays.toString(aesIv) +
                ", bt=" + Arrays.toString(bt) +
                '}';
    }
}
