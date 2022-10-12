package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TimeExecution {
    private long executionInMs;
    private String testId;

    public String executionTimeString() {
        return testId + " - " + executionInMs + "ms";
    }

    @Override
    public String toString() {
        return executionTimeString();
    }
}
