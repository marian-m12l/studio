package studio.core.v1.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SecurityUtils {

    private static final Logger LOGGER = LogManager.getLogger(SecurityUtils.class);

    private static final byte[] HEX_ARRAY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    private SecurityUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Compute the sha1 of a byte array in Hex string
     * 
     * @param input byte array
     * @return sha1 String
     */
    public static String sha1Hex(byte[] array) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA1").digest(array);
            return encodeHex(digest);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("sha1 not supported", e);
            return null;
        }
    }

    /**
     * Compute the sha1 of a String in Hex string
     * 
     * @param input UTF-8 string
     * @return sha1 String
     */
    public static String sha1Hex(String s) {
        return sha1Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convert byte array to (lowercase) Hex String
     * 
     * @param bytes byte array
     * @return hexadecimal string
     */
    public static String encodeHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    /**
     * Convert UTF-8 String to (lowercase) Hex String
     * 
     * @param s String
     * @return hexadecimal string
     */
    public static String encodeHex(String s) {
        return encodeHex(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convert Hex String to byte array.
     * 
     * @param s hexadecimal string
     * @return byte array
     */
    public static byte[] decodeHex(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string (length % 2 != 0)");
        }
        byte[] arr = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            arr[i / 2] = Integer.valueOf(s.substring(i, i + 2), 16).byteValue();
        }
        return arr;
    }

}
