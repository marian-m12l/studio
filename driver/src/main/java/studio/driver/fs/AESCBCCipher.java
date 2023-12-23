package studio.driver.fs;

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
import java.util.logging.Logger;

public class AESCBCCipher {
    private static final Logger LOGGER = Logger.getLogger(AESCBCCipher.class.getName());

    public static byte[] reverseBytes(byte[] bytes) {
        int gs = 4;
        if(bytes.length % gs != 0) {
            return bytes;
        }
        byte[] bytesReversed = new byte[bytes.length];
        for (int i=0; i < bytes.length; ++i) {
            int j = i / gs, k = i % gs;
            bytesReversed[i] = bytes[j * gs - 1 + gs - k];
        }
        return bytesReversed;
    }

    public static byte[] cipher(byte[] data, byte[] deviceKey) {
        if (deviceKey == null || deviceKey.length == 0) {
            return data;
        }
        byte[] key = new byte[16];
        byte[] keyIV = new byte[16];
        System.arraycopy(deviceKey, 0, key, 0, 16);
        System.arraycopy(deviceKey, 16, keyIV, 0, 16);
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        byte[] blockCiphered = AESCBCCipher.encrypt(
                block,
                key,
                keyIV
        );
        byte[] dataCiphered = new byte[Math.max(data.length, blockCiphered.length)];
        System.arraycopy(blockCiphered, 0, dataCiphered, 0, blockCiphered.length);
        System.arraycopy(data, block.length, dataCiphered, blockCiphered.length, data.length - block.length);
        return dataCiphered;
    }

    public static byte[] decipher(byte[] data, byte[] deviceKey) {
        if (deviceKey == null || deviceKey.length == 0) {
            return data;
        }
        byte[] key = new byte[16];
        byte[] keyIV = new byte[16];
        System.arraycopy(deviceKey, 0, key, 0, 16);
        System.arraycopy(deviceKey, 16, keyIV, 0, 16);
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        byte[] blockDeciphered = AESCBCCipher.decrypt(
                block,
                key,
                keyIV
        );
        byte[] dataDeciphered = new byte[Math.max(data.length, blockDeciphered.length)];
        System.arraycopy(blockDeciphered, 0, dataDeciphered, 0, blockDeciphered.length);
        System.arraycopy(data, block.length, dataDeciphered, blockDeciphered.length, data.length - block.length);
        return dataDeciphered;
    }

    public static byte[] encrypt(byte[] bytes, byte[] key, byte[] keyIV) {
        LOGGER.fine("AESCBCCipher encrypt data");
        return crypt(bytes, key, keyIV, Cipher.ENCRYPT_MODE);
    }

    public static byte[] decrypt(byte[] bytes, byte[] key, byte[] keyIV) {
        LOGGER.fine("AESCBCCipher decrypt data");
        return crypt(bytes, key, keyIV, Cipher.DECRYPT_MODE);
    }

    private static byte[] crypt(byte[] bytes, byte[] key, byte[] keyIV, int mode) {
        if(bytes.length < 512) {
            int bytes16 = 16 - bytes.length % 16;
            if (bytes16 < 16) {
                byte[] liTmp = bytes;
                bytes = new byte[liTmp.length + bytes16];
                System.arraycopy(liTmp, 0, bytes, 0, liTmp.length);
            }
        }

        try {
            IvParameterSpec iv = new IvParameterSpec(AESCBCCipher.reverseBytes(keyIV));
            SecretKeySpec sKey = new SecretKeySpec(AESCBCCipher.reverseBytes(key), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(mode, sKey, iv);
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

}
