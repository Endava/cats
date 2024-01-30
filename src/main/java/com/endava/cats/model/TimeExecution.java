package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Holds a mapping between test identifiers and execution times in ms.
 */
@Builder
@Getter
public class TimeExecution {
    private long executionInMs;
    private String testId;

    /**
     * Generates a string representation of the execution time, including the test identifier and duration in milliseconds.
     *
     * @return A formatted string representing the test identifier and execution time in milliseconds.
     */
    public String executionTimeString() {
        return testId + " - " + executionInMs + "ms";
    }

    @Override
    public String toString() {
        return executionTimeString();
    }
}
