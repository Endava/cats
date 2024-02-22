package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;
import java.util.Objects;

/**
 * Represents the execution details of a custom fuzzer. It includes information about the fuzzing data,
 * the associated test ID, and the test entry object.
 */
@Builder
@Getter
public class CustomFuzzerExecution implements Comparable<CustomFuzzerExecution> {
    private final FuzzingData fuzzingData;
    private final String testId;
    private final Object testEntry;

    @Override
    public int compareTo(CustomFuzzerExecution o) {
        return Comparator.comparing((CustomFuzzerExecution c) -> c.getFuzzingData().getPath())
                .thenComparing(CustomFuzzerExecution::getTestId)
                .compare(this, o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
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
