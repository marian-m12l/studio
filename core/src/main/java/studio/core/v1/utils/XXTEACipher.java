/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class XXTEACipher {

    private static final int DELTA = 0x9e3779b9;

    private static final byte[] COMMON_KEY = SecurityUtils.decodeHex("91bd7a0aa75440a9bbd49d6ce0dcc0e3");

    public enum CipherMode {
        CIPHER, DECIPHER
    }

    private XXTEACipher() {
        throw new IllegalArgumentException("Utility class");
    }

    /** (De-)cipher a block of data with a key. */
    public static byte[] cipher(CipherMode mode, byte[] data, int minSize, byte[] key) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(minSize, data.length));
        int[] dataInt = toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] keyInt = toIntArray(key, ByteOrder.BIG_ENDIAN);
        int op = Math.min(128, data.length / 4);
        int[] encryptedInt = btea(dataInt, mode == CipherMode.DECIPHER ? -op : op, keyInt);
        return toByteArray(encryptedInt, ByteOrder.LITTLE_ENDIAN);
    }

    /** (De-)cipher data with the common key. */
    public static byte[] cipherCommonKey(CipherMode mode, byte[] data) {
        byte[] encryptedBlock = cipher(mode, data, 512, COMMON_KEY);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(encryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }

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

    public static int[] btea(int[] v, int n, int[] k) {
        int y;
        int z;
        int sum;
        int p;
        int rounds;
        int e;
        if (n > 1) {          /* Coding Part */
            rounds = 1 + 52/n;
            sum = 0;
            z = v[n-1];
            do {
                sum += DELTA;
                e = (sum >>> 2) & 3;
                for (p=0; p<n-1; p++) {
                    y = v[p+1];
                    z = v[p] += mx(k, e, p, y, z, sum);
                }
                y = v[0];
                z = v[n-1] += mx(k, e, p, y, z, sum);
            } while (--rounds != 0);
        } else if (n < -1) {  /* Decoding Part */
            n = -n;
            rounds = 1 + 52/n;
            sum = rounds*DELTA;
            y = v[0];
            do {
                e = (sum >>> 2) & 3;
                for (p=n-1; p>0; p--) {
                    z = v[p-1];
                    y = v[p] -= mx(k, e, p, y, z, sum);
                }
                z = v[n-1];
                y = v[0] -= mx(k, e, p, y, z, sum);
                sum -= DELTA;
            } while (--rounds != 0);
        }
        return v;
    }

    private static int mx(int[] k, int e, int p, int y, int z, int sum) {
        return (((z>>>5^y<<2) + (y>>>3^z<<4)) ^ ((sum^y) + (k[(p&3)^e] ^ z)));
    }
}
