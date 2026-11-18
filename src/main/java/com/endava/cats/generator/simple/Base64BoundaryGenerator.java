package com.endava.cats.generator.simple;

import java.util.List;
import java.util.stream.IntStream;

/** Bounded decoder inputs shared by field and authentication-envelope robustness probes. */
public final class Base64BoundaryGenerator {
    private static final List<String> VALUES = IntStream.of(64, 256).mapToObj(Base64BoundaryGenerator::atBoundary).flatMap(List::stream).toList();

    private Base64BoundaryGenerator() {
    }

    public static List<String> values() {
        return VALUES;
    }

    private static List<String> atBoundary(int length) {
        String prefix = "A".repeat(length);
        String shortPrefix = prefix.substring(0, length - 1);
        return List.of(shortPrefix, shortPrefix + "=", prefix, prefix + "=", prefix + "A", shortPrefix + "==");
    }
}
