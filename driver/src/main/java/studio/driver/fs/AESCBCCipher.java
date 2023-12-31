/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import studio.core.v1.utils.BytesUtils;
import studio.driver.model.fs.FsDeviceKeyV3;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class AESCBCCipher {

    private static final int AES_CBC_BLOCK_SIZE = 16;

    public static byte[] cipher(byte[] bytes, FsDeviceKeyV3 deviceKeyV3) {
        return cbc(bytes, deviceKeyV3, Cipher.ENCRYPT_MODE);
    }

    public static byte[] decipher(byte[] bytes, FsDeviceKeyV3 deviceKeyV3) {
        return cbc(bytes, deviceKeyV3, Cipher.DECRYPT_MODE);
    }

    private static byte[] cbc(byte[] bytes, FsDeviceKeyV3 deviceKeyV3, int mode) {
        // Zero-bytes padding (keeping the padding when deciphering should be OK if that's what the firmware expects anyway)
        int incompleteBlockLength = bytes.length % AES_CBC_BLOCK_SIZE;
        if (incompleteBlockLength > 0) {
            int paddingLength = AES_CBC_BLOCK_SIZE - incompleteBlockLength;
            bytes = Arrays.copyOf(bytes, bytes.length + paddingLength);
        }
        try {
            IvParameterSpec iv = new IvParameterSpec(BytesUtils.reverseEndianness(deviceKeyV3.getAesIv()));
            SecretKeySpec sKey = new SecretKeySpec(BytesUtils.reverseEndianness(deviceKeyV3.getAesKey()), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(mode, sKey, iv);
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

}
