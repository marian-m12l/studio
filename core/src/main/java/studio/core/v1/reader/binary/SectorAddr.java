/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.binary;

import java.util.Objects;

public class SectorAddr implements Comparable<SectorAddr> {

    private final int offset;

    public SectorAddr(int offset) {
        this.offset = offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SectorAddr that = (SectorAddr) o;
        return offset == that.offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset);
    }

    @Override
    public int compareTo(SectorAddr o) {
        return this.offset - o.offset;
    }

    public int getOffset() {
        return offset;
    }
}
