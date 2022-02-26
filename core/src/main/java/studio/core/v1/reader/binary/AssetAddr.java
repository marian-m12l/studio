/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.binary;

import java.util.Objects;

public class AssetAddr implements Comparable<AssetAddr> {

    private final AssetType type;
    private final int offset;
    private final int size;

    public enum AssetType {
        AUDIO,
        IMAGE
    }

    public AssetAddr(AssetType type, int offset, int size) {
        this.type = type;
        this.offset = offset;
        this.size = size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssetAddr assetAddr = (AssetAddr) o;
        return offset == assetAddr.offset &&
                size == assetAddr.size &&
                type == assetAddr.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, size, type);
    }

    @Override
    public int compareTo(AssetAddr o) {
        return this.offset - o.offset;
    }

    public AssetType getType() {
        return type;
    }

    public int getOffset() {
        return offset;
    }

    public int getSize() {
        return size;
    }
}
