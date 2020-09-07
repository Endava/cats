package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CustomFuzzerExecution {
    private FuzzingData fuzzingData;
    private String testId;
    private Object testEntry;
}
