package com.endava.cats.model.report;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CatsTestReport {
    private final List<CatsTestCaseSummary> summaryList;
    private final int totalTests;
    private final int success;
    private final int warnings;
    private final int errors;
    private final String timestamp;

}
