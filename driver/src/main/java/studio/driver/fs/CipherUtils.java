/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import studio.core.v1.reader.fs.FsStoryPackReader;
import studio.core.v1.utils.BytesUtils;
import studio.core.v1.utils.XXTEACipher;
import studio.driver.model.fs.FsDeviceKeyV3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
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

    private static final int CIPHER_BLOCK_SIZE_BOOT_V2 = 64;
    private static final int CIPHER_BLOCK_SIZE_ASSETS_V2 = 512;
    private static final int CIPHER_BLOCK_SIZE_ASSETS_V3 = 512;

    private static final String BOOT_FILENAME = "bt";

    private static final List<String> CLEAR_FILES = List.of(FsStoryPackReader.NODE_INDEX_FILENAME, FsStoryPackReader.NIGHT_MODE_FILENAME, FsStoryPackReader.CLEARTEXT_FILENAME);
    private static final List<String> NO_COPY_FILES = List.of(FsStoryPackReader.CLEARTEXT_FILENAME);

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
        FileInputStream riFis = new FileInputStream(new File(packFolder.toFile(), FsStoryPackReader.IMAGE_INDEX_FILENAME));
        byte[] riCipheredBlock = riFis.readNBytes(CIPHER_BLOCK_SIZE_BOOT_V2);
        riFis.close();
        // Add boot file: bt
        FileOutputStream btFos = new FileOutputStream(new File(packFolder.toFile(), BOOT_FILENAME));
        // The first **scrambled** 64 bytes of 'ri' file must be ciphered with the device-specific key into 'bt' file
        byte[] btCiphered = cipherFirstBlockSpecificKeyV2(riCipheredBlock, specificKey);
        btFos.write(btCiphered);
        btFos.close();
    }

    static void addBootFileV3(Path packFolder, FsDeviceKeyV3 deviceKeyV3) throws IOException {
        // Add boot file: bt
        Files.write(new File(packFolder.toFile(), BOOT_FILENAME).toPath(), deviceKeyV3.getBt());
    }

    static byte[] cipherFirstBlockCommonKey(byte[] data) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(CIPHER_BLOCK_SIZE_ASSETS_V2, data.length));
        int[] dataInt = BytesUtils.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] encryptedInt = XXTEACipher.btea(dataInt, Math.min(CIPHER_BLOCK_SIZE_ASSETS_V2/4, data.length/4), BytesUtils.toIntArray(XXTEACipher.COMMON_KEY, ByteOrder.BIG_ENDIAN));
        byte[] encryptedBlock = BytesUtils.toByteArray(encryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(encryptedBlock);
        if (data.length > CIPHER_BLOCK_SIZE_ASSETS_V2) {
            bb.put(Arrays.copyOfRange(data, CIPHER_BLOCK_SIZE_ASSETS_V2, data.length));
        }
        return bb.array();
    }
    static byte[] decipherFirstBlockCommonKey(byte[] data) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(CIPHER_BLOCK_SIZE_ASSETS_V2, data.length));
        int[] dataInt = BytesUtils.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] decryptedInt = XXTEACipher.btea(dataInt, -(Math.min(CIPHER_BLOCK_SIZE_ASSETS_V2/4, data.length/4)), BytesUtils.toIntArray(XXTEACipher.COMMON_KEY, ByteOrder.BIG_ENDIAN));
        byte[] decryptedBlock = BytesUtils.toByteArray(decryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(decryptedBlock);
        if (data.length > CIPHER_BLOCK_SIZE_ASSETS_V2) {
            bb.put(Arrays.copyOfRange(data, CIPHER_BLOCK_SIZE_ASSETS_V2, data.length));
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
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(CIPHER_BLOCK_SIZE_BOOT_V2, data.length));
        int[] dataInt = BytesUtils.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] encryptedInt = XXTEACipher.btea(dataInt, Math.min(CIPHER_BLOCK_SIZE_BOOT_V2/4, data.length/4), BytesUtils.toIntArray(specificKey, ByteOrder.BIG_ENDIAN));
        byte[] encryptedBlock = BytesUtils.toByteArray(encryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(encryptedBlock);
        if (data.length > CIPHER_BLOCK_SIZE_BOOT_V2) {
            bb.put(Arrays.copyOfRange(data, CIPHER_BLOCK_SIZE_BOOT_V2, data.length));
        }
        return bb.array();
    }

    static byte[] cipherFirstBlockSpecificKeyV3(byte[] data, FsDeviceKeyV3 deviceKeyV3) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(CIPHER_BLOCK_SIZE_ASSETS_V3, data.length));
        byte[] encryptedBlock = AESCBCCipher.cipher(block, deviceKeyV3);
        int outputLength = encryptedBlock.length + Math.max(0, data.length - CIPHER_BLOCK_SIZE_ASSETS_V3);
        ByteBuffer bb = ByteBuffer.allocate(outputLength);
        bb.put(encryptedBlock);
        if (data.length > CIPHER_BLOCK_SIZE_ASSETS_V3) {
            bb.put(Arrays.copyOfRange(data, CIPHER_BLOCK_SIZE_ASSETS_V3, data.length));
        }
        return bb.array();
    }
    static byte[] decipherFirstBlockSpecificKeyV3(byte[] data, FsDeviceKeyV3 deviceKeyV3) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(CIPHER_BLOCK_SIZE_ASSETS_V3, data.length));
        byte[] decryptedBlock = AESCBCCipher.decipher(block, deviceKeyV3);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(decryptedBlock);
        if (data.length > CIPHER_BLOCK_SIZE_ASSETS_V3) {
            bb.put(Arrays.copyOfRange(data, CIPHER_BLOCK_SIZE_ASSETS_V3, data.length));
        }
        return bb.array();
    }

}
