package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseExecutionSummary;
import com.endava.cats.model.CatsTestCaseSummary;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;

/**
 * A class responsible for generating test reports based on the provided test case exporters and reporting arguments.
 * It initializes the report generation path, writes test cases, helper files, summaries, errors by reason, and performance reports.
 */
@Singleton
public class TestReportsGenerator {
    private final List<TestCaseExporter> testCaseExporters;
    private final ExecutionStatisticsListener executionStatisticsListener;

    /**
     * Constructs a new TestReportsGenerator with the specified test case exporters and reporting arguments.
     *
     * @param testCaseExporters  the instance of TestCaseExporter to be used for generating reports
     * @param reportingArguments the reporting arguments that determine the report format
     * @param executionStatisticsListener the execution statistics listener
     */
    public TestReportsGenerator(Instance<TestCaseExporter> testCaseExporters, ReportingArguments reportingArguments,
                                ExecutionStatisticsListener executionStatisticsListener) {
        this.testCaseExporters = testCaseExporters.stream()
                .filter(exporter -> reportingArguments.getReportFormat().contains(exporter.reportFormat()))
                .toList();
        this.executionStatisticsListener = executionStatisticsListener;
    }


    /**
     * Initializes the path for the test case exporters to write reports.
     *
     * @param folder the folder where the reports will be written
     * @throws IOException if an I/O error occurs while initializing the path
     */
    public void initPath(String folder) throws IOException {
        for (TestCaseExporter exporter : testCaseExporters) {
            exporter.initPath(folder);
        }
    }

    /**
     * Writes a test case to the report using the first available test case exporter.
     *
     * @param catsTestCase the test case to be written to the report
     */
    public void writeTestCase(CatsTestCase catsTestCase) {
        testCaseExporters.getFirst().writeTestCase(catsTestCase);
    }

    /**
     * Writes helper files for the test case exporters.
     */
    public void writeHelperFiles() {
        testCaseExporters.forEach(TestCaseExporter::writeHelperFiles);
    }

    /**
     * Writes a summary of the test case execution details.
     *
     * @param testCaseSummaryDetails the list of test case summaries to be written
     */
    public void writeSummary(List<CatsTestCaseSummary> testCaseSummaryDetails) {
        testCaseExporters.forEach(testCaseExporter -> testCaseExporter.writeSummary(testCaseSummaryDetails, executionStatisticsListener));
    }

    /**
     * Writes errors by reason for the provided test case summary details.
     *
     * @param testCaseSummaryDetails the list of test case summaries containing error details
     */
    public void writeErrorsByReason(List<CatsTestCaseSummary> testCaseSummaryDetails) {
        testCaseExporters.getFirst().writeErrorsByReason(testCaseSummaryDetails);
    }


    /**
     * Writes a performance report based on the provided test case execution details.
     *
     * @param testCaseExecutionDetails the list of test case execution summaries to be included in the performance report
     */
    public void writePerformanceReport(List<CatsTestCaseExecutionSummary> testCaseExecutionDetails) {
        testCaseExporters.getFirst().writePerformanceReport(testCaseExecutionDetails);
    }

    /**
     * Prints the execution details using the first available test case exporter.
     */
    public void printExecutionDetails() {
        testCaseExporters.getFirst().printExecutionDetails();
    }

    public void writeTopFuzzers(List<CatsTestCaseSummary> testCaseSummaries) {
        testCaseExporters.getFirst().writeTopFuzzers(testCaseSummaries);
    }
}
