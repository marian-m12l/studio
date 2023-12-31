/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

public class XXTEACipher {

    public static final byte[] COMMON_KEY = new byte[] { (byte)0x91, (byte)0xbd, (byte)0x7a, (byte)0x0a, (byte)0xa7, (byte)0x54, (byte)0x40, (byte)0xa9, (byte)0xbb, (byte)0xd4, (byte)0x9d, (byte)0x6c, (byte)0xe0, (byte)0xdc, (byte)0xc0, (byte)0xe3};

    private static final int DELTA = 0x9e3779b9;

    public static int[] btea(int[] v, int n, int[] k) {
        int y, z, sum;
        int p, rounds, e;
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
