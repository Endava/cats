package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.exception.CatsException;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseExecutionSummary;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A class responsible for generating test reports based on the provided test case exporters and reporting arguments.
 * It initializes the report generation path, writes test cases, helper files, summaries, errors by reason, and performance reports.
 */
@Singleton
public class TestReportsGenerator {
    private final List<TestCaseExporter> testCaseExporters;
    private final TestCaseExporter primaryExporter;

    /**
     * Constructs a new TestReportsGenerator with the specified test case exporters and reporting arguments.
     *
     * @param testCaseExporters the instance of TestCaseExporter to be used for generating reports
     * @param reportingArguments the reporting arguments that determine the report format
     */
    public TestReportsGenerator(Instance<TestCaseExporter> testCaseExporters,
                                ReportingArguments reportingArguments) {
        Map<ReportingArguments.ReportFormat, TestCaseExporter> exportersByFormat = testCaseExporters.stream()
                .collect(Collectors.toMap(TestCaseExporter::reportFormat, Function.identity(),
                        (first, second) -> {
                            throw new CatsException("Multiple report exporters registered for format " + first.reportFormat());
                        }, LinkedHashMap::new));
        List<ReportingArguments.ReportFormat> selectedFormats = reportingArguments.getReportFormat().stream()
                .distinct()
                .toList();
        if (selectedFormats.isEmpty()) {
            throw new CatsException("At least one report format must be selected");
        }
        this.primaryExporter = findExporter(exportersByFormat, selectedFormats.iterator().next());
        this.testCaseExporters = selectedFormats.stream()
                .map(format -> findExporter(exportersByFormat, format))
                .toList();
    }

    private TestCaseExporter findExporter(Map<ReportingArguments.ReportFormat, TestCaseExporter> exportersByFormat,
                                          ReportingArguments.ReportFormat format) {
        TestCaseExporter exporter = exportersByFormat.get(format);
        if (exporter == null) {
            throw new CatsException("No report exporter registered for format " + format);
        }
        return exporter;
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
     * Writes a format-independent test case artifact using the explicitly selected primary exporter.
     *
     * @param catsTestCase the test case to be written to the report
     */
    public void writeTestCase(CatsTestCase catsTestCase) {
        primaryExporter.writeTestCase(catsTestCase);
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
     * @param executionSummary the single execution snapshot shared by every exporter
     */
    public void writeSummary(List<CatsTestCaseSummary> testCaseSummaryDetails, ExecutionSummary executionSummary) {
        testCaseExporters.forEach(testCaseExporter ->
                testCaseExporter.writeSummary(testCaseSummaryDetails, executionSummary));
    }

    /**
     * Writes errors by reason for the provided test case summary details.
     *
     * @param testCaseSummaryDetails the list of test case summaries containing error details
     */
    public void writeErrorsByReason(List<CatsTestCaseSummary> testCaseSummaryDetails) {
        primaryExporter.writeErrorsByReason(testCaseSummaryDetails);
    }


    /**
     * Writes a performance report based on the provided test case execution details.
     *
     * @param testCaseExecutionDetails the list of test case execution summaries to be included in the performance report
     */
    public void writePerformanceReport(List<CatsTestCaseExecutionSummary> testCaseExecutionDetails) {
        primaryExporter.writePerformanceReport(testCaseExecutionDetails);
    }

    /**
     * Prints execution details through the explicitly selected primary exporter.
     */
    public void printExecutionDetails(ExecutionSummary executionSummary) {
        primaryExporter.printExecutionDetails(executionSummary);
    }

    public void writeTopFuzzers(List<CatsTestCaseSummary> testCaseSummaries) {
        primaryExporter.writeTopFuzzers(testCaseSummaries);
    }
}
