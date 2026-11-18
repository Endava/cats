package com.endava.cats.generator.simple;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Public synthetic authentication values; no supplied credential contributes to this corpus. */
public final class EncodedAuthenticationGenerator {
    private static final List<String> JWT_PARTS = List.of("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", "e30", "c2ln");
    private static final List<String> BASIC_VALUES = Stream.concat(Stream.of("Basic Y2F0czpjYXRz"),
            Base64BoundaryGenerator.values().stream().map(value -> "Basic " + value)).toList();
    private static final List<String> JWT_VALUES = Stream.concat(Stream.of("Bearer " + String.join(".", JWT_PARTS)),
            IntStream.range(0, 3).boxed().flatMap(index -> Base64BoundaryGenerator.values().stream().map(value -> jwt(index, value)))).toList();
    private static final Set<String> VALUES = Stream.concat(BASIC_VALUES.stream(), JWT_VALUES.stream()).collect(Collectors.toUnmodifiableSet());

    private EncodedAuthenticationGenerator() {
    }

    public static List<String> basicValues() {
        return BASIC_VALUES;
    }

    public static List<String> jwtValues() {
        return JWT_VALUES;
    }

    public static boolean isSyntheticValue(Object value) {
        return value instanceof String text && VALUES.contains(text);
    }

    private static String jwt(int selected, String value) {
        return "Bearer " + IntStream.range(0, 3).mapToObj(index -> index == selected ? value : JWT_PARTS.get(index))
                .collect(Collectors.joining("."));
    }
}
