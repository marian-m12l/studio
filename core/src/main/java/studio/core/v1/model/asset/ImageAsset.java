/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.asset;

public class ImageAsset {

    private ImageType type;
    private byte[] rawData;

    public ImageAsset(ImageType type, byte[] rawData) {
        super();
        this.type = type;
        this.rawData = rawData;
    }

    public ImageType getType() {
        return type;
    }

    public void setType(ImageType imageType) {
        this.type = imageType;
    }

    public byte[] getRawData() {
        return rawData;
    }

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

}
