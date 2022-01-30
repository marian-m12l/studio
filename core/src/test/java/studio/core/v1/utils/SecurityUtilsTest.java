package studio.core.v1.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SecurityUtilsTest {

    @Test
    void sha1Hex() {
        String s = "Hello world";
        byte[] a = s.getBytes();
        String expected = "7b502c3a1f48c8609ae212cdfb639dee39673f5e";

        long t1 = System.nanoTime();
        String actual = SecurityUtils.sha1Hex(a);
        long t2 = System.nanoTime();
        System.out.printf("sha1Hex (%s ns) : %s -> %s\n", t2 - t1, s, actual);

        assertEquals(expected, actual, "sha1");
    }

    @Test
    void decodeHex() {
        String s = "48656c6c6f20776f726c64";
        byte[] expected = "Hello world".getBytes();

        long t1 = System.nanoTime();
        byte[] actual = SecurityUtils.decodeHex(s);
        long t2 = System.nanoTime();
        System.out.printf("decodeHex (%s ns) : %s -> %s\n", t2 - t1, s, new String(actual));

        assertArrayEquals(expected, actual, "decodeHex");
    }

    @Test
    void encodeHex() {
        String s = "Hello world";
        byte[] a = s.getBytes();
        String expected = "48656c6c6f20776f726c64";
        long t1 = System.nanoTime();
        String actual = SecurityUtils.encodeHex(a);
        long t2 = System.nanoTime();
        System.out.printf("encodeHex (%s ns) : %s -> %s\n", t2 - t1, s, "");
        assertEquals(expected, actual, "encodeHex");
    }

}
