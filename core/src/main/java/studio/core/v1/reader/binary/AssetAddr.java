/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.binary;

import studio.core.v1.model.AssetType;

import java.util.Objects;

public class AssetAddr implements Comparable<AssetAddr> {

    private int offset;
    private int size;
    private AssetType type;

    public AssetAddr() {
    }

    public AssetAddr(int offset, int size, AssetType type) {
        this.offset = offset;
        this.size = size;
        this.type = type;
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

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public AssetType getType() {
        return type;
    }

    public void setType(AssetType type) {
        this.type = type;
    }
}
