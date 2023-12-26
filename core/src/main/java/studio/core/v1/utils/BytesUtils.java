/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class BytesUtils {

    public static int[] toIntArray(byte[] data, ByteOrder endianness) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(endianness);
        List<Integer> ints = new ArrayList<>();
        for (int i=0; i<data.length/4; i++) {
            ints.add(bb.getInt());
        }
        return ints.stream().mapToInt(i->i).toArray();
    }

    public static byte[] toByteArray(int[] data, ByteOrder endianness) {
        ByteBuffer bb = ByteBuffer.allocate(data.length*4);
        bb.order(endianness);
        for (int i : data) {
            bb.putInt(i);
        }
        return bb.array();
    }

    public static byte[] reverseEndianness(byte[] data) {
        return toByteArray(toIntArray(data, ByteOrder.LITTLE_ENDIAN), ByteOrder.BIG_ENDIAN);
    }
}
