/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.asset;

public class AudioAsset {

    private AudioType type;
    private byte[] rawData;

    public AudioAsset(AudioType type, byte[] rawData) {
        super();
        this.type = type;
        this.rawData = rawData;
    }

    public AudioType getType() {
        return type;
    }

    public void setType(AudioType type) {
        this.type = type;
    }

    public byte[] getRawData() {
        return rawData;
    }

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

}
