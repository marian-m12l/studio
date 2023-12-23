/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import studio.core.v1.utils.XXTEACipher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/*
On firmware V2:
    The first 512 bytes of most files are ciphered (XXTEA) with a common key. The bt file ciphers (XXTEA) the first
    64 bytes of ri file using a device-specific key.
On firmware V3:
    The first 512 bytes of most files are ciphered (AES/CBC) with a device-specific key. The bt file contains part of
    this device-specific key.
 */
public class CipherUtils {

    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String LIST_INDEX_FILENAME = "li";
    private static final String IMAGE_INDEX_FILENAME = "ri";
    private static final String IMAGE_FOLDER = "rf" + File.separator;
    private static final String SOUND_INDEX_FILENAME = "si";
    private static final String SOUND_FOLDER = "sf" + File.separator;
    private static final String BOOT_FILENAME = "bt";
    private static final String NIGHT_MODE_FILENAME = "nm";
    static final String CLEARTEXT_FILENAME = ".cleartext";

    private static final List<String> CLEAR_FILES = List.of(NODE_INDEX_FILENAME, NIGHT_MODE_FILENAME, CLEARTEXT_FILENAME);
    private static final List<String> NO_COPY_FILES = List.of(CLEARTEXT_FILENAME);

    static boolean shouldBeCopied(Path filePath) {
        return !NO_COPY_FILES.contains(filePath.getFileName().toString());
    }

    static boolean shouldBeCiphered(Path filePath) {
        return !CLEAR_FILES.contains(filePath.getFileName().toString());
    }

    static void addBootFileV2(Path packFolder, byte[] deviceUuid) throws IOException {
        // Compute specific key
        byte[] specificKey = computeSpecificKeyV2FromUUID(deviceUuid);
        // Read ciphered block of ri file
        FileInputStream riFis = new FileInputStream(new File(packFolder.toFile(), IMAGE_INDEX_FILENAME));
        byte[] riCipheredBlock = riFis.readNBytes(64);
        riFis.close();
        // Add boot file: bt
        FileOutputStream btFos = new FileOutputStream(new File(packFolder.toFile(), BOOT_FILENAME));
        // The first **scrambled** 64 bytes of 'ri' file must be ciphered with the device-specific key into 'bt' file
        byte[] btCiphered = cipherFirstBlockSpecificKeyV2(riCipheredBlock, specificKey);
        btFos.write(btCiphered);
        btFos.close();
    }

    static void addBootFileV3(Path packFolder, byte[] deviceKeyV3) throws IOException {
        // Add boot file: bt
        FileOutputStream btFos = new FileOutputStream(new File(packFolder.toFile(), BOOT_FILENAME));
        // Copy second half of device key
        byte[] btContent = new byte[32];
        System.arraycopy(deviceKeyV3, 32, btContent, 0, 32);
        btFos.write(btContent);
        btFos.close();
    }

    static byte[] cipherFirstBlockCommonKey(byte[] data) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        int[] dataInt = XXTEACipher.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] encryptedInt = XXTEACipher.btea(dataInt, Math.min(128, data.length/4), XXTEACipher.toIntArray(XXTEACipher.COMMON_KEY, ByteOrder.BIG_ENDIAN));
        byte[] encryptedBlock = XXTEACipher.toByteArray(encryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(encryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }
    static byte[] decipherFirstBlockCommonKey(byte[] data) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        int[] dataInt = XXTEACipher.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] decryptedInt = XXTEACipher.btea(dataInt, -(Math.min(128, data.length/4)), XXTEACipher.toIntArray(XXTEACipher.COMMON_KEY, ByteOrder.BIG_ENDIAN));
        byte[] decryptedBlock = XXTEACipher.toByteArray(decryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(decryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }
    private static byte[] computeSpecificKeyV2FromUUID(byte[] uuid) {
        byte[] btKey = decipherFirstBlockCommonKey(uuid);
        byte[] reorderedBtKey = new byte[]{
                btKey[11], btKey[10], btKey[9], btKey[8],
                btKey[15], btKey[14], btKey[13], btKey[12],
                btKey[3], btKey[2], btKey[1], btKey[0],
                btKey[7], btKey[6], btKey[5], btKey[4]
        };
        return reorderedBtKey;
    }
    static byte[] cipherFirstBlockSpecificKeyV2(byte[] data, byte[] specificKey) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(64, data.length));
        int[] dataInt = XXTEACipher.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] encryptedInt = XXTEACipher.btea(dataInt, Math.min(128, data.length/4), XXTEACipher.toIntArray(specificKey, ByteOrder.BIG_ENDIAN));
        return XXTEACipher.toByteArray(encryptedInt, ByteOrder.LITTLE_ENDIAN);
    }
    static byte[] cipherFirstBlockSpecificKeyV3(byte[] data, byte[] deviceKeyV3) {
        /*byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        byte[] encryptedBlock = AESCBCCipher.cipher(block, deviceKeyV3);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(encryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();*/
        return AESCBCCipher.cipher(data, deviceKeyV3);
    }
    static byte[] decipherFirstBlockSpecificKeyV3(byte[] data, byte[] deviceKeyV3) {
        /*byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        byte[] decryptedBlock = AESCBCCipher.decipher(block, deviceKeyV3);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(decryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();*/
        return AESCBCCipher.decipher(data, deviceKeyV3);
    }

}
