package studio.core.v1.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

public class SecurityUtilsTest {

    @Test
    void sha1Hex() throws NoSuchAlgorithmException, UnsupportedEncodingException {
        byte[] a = "Hello world".getBytes();
        String expected = "7b502c3a1f48c8609ae212cdfb639dee39673f5e";

        long t1 = System.nanoTime();
        String actual = SecurityUtils.sha1Hex(a);
        long t2 = System.nanoTime();
        System.out.printf("sha1Hex v2 (%s ms) : %s\n", t2 - t1, actual);

        assertEquals(expected, actual, "sha1");
    }

    @Test
    void decodeHex() throws DecoderException {
        String s = "48656c6c6f20776f726c64";
        byte[] expected = "Hello world".getBytes();

        long t1 = System.nanoTime();
        byte[] actual = SecurityUtils.decodeHex(s);
        long t2 = System.nanoTime();
        byte[] hex = Hex.decodeHex(s);
        long t3 = System.nanoTime();

        System.out.printf("decodeHex v1 (%s ms) : %s\n", t2 - t1, new String(actual));
        System.out.printf("decodeHex v2 (%s ms) : %s\n", t3 - t2, new String(hex));

        assertArrayEquals(expected, actual, "decodeHex");
    }

    @Test
    void encodeHex() {
        byte[] a = "Hello world".getBytes();
        String expected = "48656c6c6f20776f726c64";

        long t1 = System.nanoTime();
        String actual = SecurityUtils.encodeHex(a);
        long t2 = System.nanoTime();
        String hex = Hex.encodeHexString(a);
        long t3 = System.nanoTime();

        System.out.printf("encodeHex v1 (%s ms) : %s\n", t2 - t1, actual);
        System.out.printf("encodeHex v0 (%s ms) : %s\n", t3 - t2, hex);

        assertEquals(expected, actual, "encodeHex");
    }

}
