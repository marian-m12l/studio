package studio.core.v1.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SecurityUtilsTest {

    // Microbenchmark function (in milliseconds)
    static <A, B> B benchCall(String name, Function<A, B> f, A a) {
        long t1 = System.nanoTime();
        B b = f.apply(a);
        long t2 = System.nanoTime();
        System.out.printf("%s (%s ms) : %s -> %s\n", name, (t2 - t1) / 1000, a, b);
        return b;
    }

    @Test
    void sha1Hex() {
        String expected = "7b502c3a1f48c8609ae212cdfb639dee39673f5e";
        String actual;

        actual = benchCall("sha1_str", SecurityUtils::sha1Hex, "Hello world");
        assertEquals(expected, actual, "sha1");

        actual = benchCall("sha1_byte", SecurityUtils::sha1Hex, "Hello world".getBytes());
        assertEquals(expected, actual, "sha1");
    }

    @Test
    void decodeHex() {
        byte[] expected = "Hello world".getBytes();
        byte[] actual;

        actual = benchCall("decodeHex", SecurityUtils::decodeHex, "48656c6c6f20776f726c64");
        assertArrayEquals(expected, actual, "decodeHex");

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            benchCall("decodeHex", SecurityUtils::decodeHex, "123");
        });
        Assertions.assertEquals("Invalid hex string (length % 2 != 0)", ex.getMessage());
    }

    @Test
    void encodeHex() {
        String expected = "48656c6c6f20776f726c64";
        String actual;

        actual = benchCall("encodeHex_str", SecurityUtils::encodeHex, "Hello world");
        assertEquals(expected, actual, "encodeHex");

        actual = benchCall("encodeHex_byte", SecurityUtils::encodeHex, "Hello world".getBytes());
        assertEquals(expected, actual, "encodeHex");
    }
}
