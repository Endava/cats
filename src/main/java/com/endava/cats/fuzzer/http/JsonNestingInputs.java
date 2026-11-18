package com.endava.cats.fuzzer.http;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Iteratively constructs small documents with deep structures, without parsing them inside CATS. */
final class JsonNestingInputs {
    private static final List<Probe> PROBES = IntStream.of(32, 128, 512, 1024).boxed()
            .flatMap(depth -> Arrays.stream(Shape.values()).flatMap(shape -> probes(depth, shape))).toList();

    private JsonNestingInputs() {
    }

    static List<Probe> probes() {
        return PROBES;
    }

    private static Stream<Probe> probes(int depth, Shape shape) {
        String open = IntStream.range(0, depth).mapToObj(shape::opening).collect(Collectors.joining());
        String close = IntStream.range(0, depth).mapToObj(index -> shape.closing(depth - index - 1)).collect(Collectors.joining());
        String complete = open + "0" + close;
        return Stream.of(new Probe(depth, shape.name(), false, complete),
                new Probe(depth, shape.name(), true, complete.substring(0, complete.length() - 1)));
    }

    record Probe(int depth, String shape, boolean truncated, String payload) {
        String description() {
            return "Send %s JSON at depth %d (%s).".formatted(shape, depth, truncated ? "missing final delimiter" : "complete");
        }
    }

    private enum Shape {
        OBJECT, ARRAY, ALTERNATING;

        String opening(int index) {
            return isObject(index) ? "{\"n\":" : "[";
        }

        String closing(int index) {
            return isObject(index) ? "}" : "]";
        }

        private boolean isObject(int index) {
            return this == OBJECT || (this == ALTERNATING && index % 2 == 0);
        }
    }
}
