/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

public class ImageAsset extends Asset {

    public ImageAsset() {
        super();
    }

    public ImageAsset(String mimeType, byte[] rawData) {
        super(mimeType, rawData);
    }

}
