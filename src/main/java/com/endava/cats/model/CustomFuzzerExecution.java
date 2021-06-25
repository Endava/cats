package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;

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
}
