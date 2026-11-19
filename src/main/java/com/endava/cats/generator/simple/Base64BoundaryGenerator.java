package com.endava.cats.generator.simple;

import com.endava.cats.util.CatsRandom;

import java.util.List;
import java.util.stream.IntStream;

/** Bounded decoder inputs shared by field and authentication-envelope robustness probes. */
public final class Base64BoundaryGenerator {
    private static final String STANDARD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String URL_SAFE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final List<Integer> RANDOM_STRIDES = List.of(16, 32, 48, 64, 128, 256);
    private static final List<String> UNDERFLOW_AND_PAD_TAILS = List.of(
            "=", "==", "===", "A=", "A==", "AB=", "AB==", "\r\n=", "-_=");
    private static final List<String> VALUES = IntStream.of(64, 128, 256)
            .mapToObj(Base64BoundaryGenerator::atBoundary)
            .flatMap(List::stream)
            .toList();

    private Base64BoundaryGenerator() {
    }

    public static List<String> values() {
        return VALUES;
    }

    /**
     * Synthesizes a deterministic-random Base64 payload combining SIMD block strides
     * with underflow padding, non-canonical pad bits, or URL-safe/MIME tail variations.
     *
     * @return a randomized Base64 boundary probe string
     */
    public static String randomBoundaryValue() {
        int stride = RANDOM_STRIDES.get(CatsRandom.instance().nextInt(RANDOM_STRIDES.size()));
        int length = Math.max(4, stride + CatsRandom.instance().nextInt(3) - 1);
        String alphabet = CatsRandom.instance().nextBoolean() ? STANDARD_ALPHABET : URL_SAFE_ALPHABET;
        String tail = UNDERFLOW_AND_PAD_TAILS.get(CatsRandom.instance().nextInt(UNDERFLOW_AND_PAD_TAILS.size()));
        return randomPrefix(alphabet, length) + tail;
    }

    private static String randomPrefix(String alphabet, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(CatsRandom.instance().nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private static List<String> atBoundary(int length) {
        String prefix = "A".repeat(length);
        String shortPrefix = prefix.substring(0, length - 1);
        return List.of(shortPrefix, shortPrefix + "=", prefix, prefix + "=", prefix + "A", shortPrefix + "==");
    }
}
