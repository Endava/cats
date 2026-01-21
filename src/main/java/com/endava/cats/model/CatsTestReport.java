package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a report summarizing the results of CATS tests.
 * This report includes information about the number of warnings, errors, and successful tests.
 */
@Getter
@Builder
public class CatsTestReport {
    private final List<CatsTestCaseSummary> testCases;
    private final long totalTests;
    private final long success;
    private final long warnings;
    private final long errors;
    private final long executionTime;
    private final String timestamp;
    private final String catsVersion;

    public List<JunitTestSuite> getTestSuites() {
        DecimalFormat decimalFormat = new DecimalFormat("#.###");

        Map<String, List<CatsTestCaseSummary>> groupedByFuzzer = testCases.stream()
                .collect(Collectors.groupingBy(CatsTestCaseSummary::getFuzzer));

        List<JunitTestSuite> junitTestSuites = new ArrayList<>();

        for (Map.Entry<String, List<CatsTestCaseSummary>> entry : groupedByFuzzer.entrySet()) {
            String fuzzer = entry.getKey();
            List<CatsTestCaseSummary> testCasesPerFuzzer = entry.getValue();

            // Compute testsuite-level details
            int totalTestsPerFuzzer = testCasesPerFuzzer.size();
            int failuresPerFuzzer = (int) testCasesPerFuzzer.parallelStream()
                    .filter(CatsTestCaseSummary::getError)
                    .filter(Predicate.not(CatsTestCaseSummary::is9xxResponse))
                    .count();
            int errorsPerFuzzer = (int) testCasesPerFuzzer.parallelStream()
                    .filter(CatsTestCaseSummary::getError)
                    .filter(CatsTestCaseSummary::is9xxResponse)
                    .count();
            int warningsPerFuzzer = (int) testCasesPerFuzzer.parallelStream().filter(CatsTestCaseSummary::getWarning).count();
            double totalTime = testCasesPerFuzzer.parallelStream().mapToDouble(CatsTestCaseSummary::getTimeToExecuteInSec).sum();

            // Create the testsuite object
            JunitTestSuite junitTestSuite = new JunitTestSuite();
            junitTestSuite.fuzzer = fuzzer;
            junitTestSuite.totalTests = totalTestsPerFuzzer;
            junitTestSuite.failures = failuresPerFuzzer;
            junitTestSuite.warnings = warningsPerFuzzer;
            junitTestSuite.errors = errorsPerFuzzer;
            junitTestSuite.time = decimalFormat.format(totalTime);
            junitTestSuite.testCases = testCasesPerFuzzer;

            junitTestSuites.add(junitTestSuite);
        }

        return junitTestSuites;
    }

    /**
     * Retrieves the number of failed tests. Failed tests are those that have an error and are not 9xx responses.
     *
     * @return The number of failed tests.
     */
    public int getFailuresJunit() {
        return (int) testCases.parallelStream()
                .filter(CatsTestCaseSummary::getError)
                .filter(Predicate.not(CatsTestCaseSummary::is9xxResponse))
                .count();
    }

    /**
     * Retrieves the number of errors. Errors are those that have an error and are 9xx responses.
     *
     * @return The number of failed tests.
     */
    public int getErrorsJunit() {
        return (int) testCases.parallelStream()
                .filter(CatsTestCaseSummary::getError)
                .filter(CatsTestCaseSummary::is9xxResponse)
                .count();
    }

    public static class JunitTestSuite {
        String fuzzer;
        int totalTests;
        int failures;
        int errors;
        int warnings;
        String time;
        List<CatsTestCaseSummary> testCases;
    }
}
