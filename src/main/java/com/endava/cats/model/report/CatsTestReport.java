package com.endava.cats.model.report;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CatsTestReport {
    private List<CatsTestCaseSummary> summaryList;
    private int totalTests;
    private int success;
    private int warnings;
    private int errors;

}
