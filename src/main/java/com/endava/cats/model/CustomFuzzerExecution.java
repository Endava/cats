package com.endava.cats.model;

import lombok.Builder;

import java.util.Comparator;
import java.util.Objects;

/**
 * Represents the execution details of a custom fuzzer. It includes information about the fuzzing data,
 * the associated test ID, and the test entry object.
 */
@Builder
public record CustomFuzzerExecution(FuzzingData fuzzingData, String testId,
                                    Object testEntry) implements Comparable<CustomFuzzerExecution> {
    @Override
    public int compareTo(CustomFuzzerExecution o) {
        return Comparator.comparing((CustomFuzzerExecution c) -> c.fuzzingData().getPath())
                .thenComparing(CustomFuzzerExecution::testId)
                .compare(this, o);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CustomFuzzerExecution that)) {
            return false;
        }
        return Objects.equals(fuzzingData.getPath(), that.fuzzingData.getPath()) && Objects.equals(testId, that.testId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fuzzingData.getPath(), testId);
    }
}
