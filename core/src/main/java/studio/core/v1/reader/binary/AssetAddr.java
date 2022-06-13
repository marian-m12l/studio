/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.binary;

import lombok.Value;

@Value
public class AssetAddr implements Comparable<AssetAddr> {

    private AssetType type;
    private int offset;
    private int size;

    public enum AssetType {
        AUDIO, IMAGE
    }

    @Override
    public int compareTo(AssetAddr o) {
        return this.offset - o.offset;
    }
}
