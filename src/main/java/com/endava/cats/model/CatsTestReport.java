package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Represents a report summarizing the results of CATS tests.
 * This report includes information about the number of warnings, errors, and successful tests.
 */
@Getter
@Builder
public class CatsTestReport {
    private final List<CatsTestCaseSummary> testCases;
    private final int totalTests;
    private final int success;
    private final int warnings;
    private final int errors;
    private final long executionTime;
    private final String timestamp;
    private final String catsVersion;
}
