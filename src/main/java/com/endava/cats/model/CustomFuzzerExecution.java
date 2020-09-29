package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CustomFuzzerExecution {
    private final FuzzingData fuzzingData;
    private final String testId;
    private final Object testEntry;
}
