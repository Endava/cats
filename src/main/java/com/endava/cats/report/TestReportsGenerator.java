package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.exception.CatsException;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseExecutionSummary;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.util.SensitiveDataPolicy;
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
    private final Map<ReportingArguments.ReportFormat, TestCaseExporter> exportersByFormat;
    private final ReportingArguments reportingArguments;

    /**
     * Constructs a new TestReportsGenerator with the specified test case exporters and reporting arguments.
     *
     * @param testCaseExporters the instance of TestCaseExporter to be used for generating reports
     * @param reportingArguments the reporting arguments that determine the report format
     */
    public TestReportsGenerator(Instance<TestCaseExporter> testCaseExporters,
                                ReportingArguments reportingArguments) {
        this.exportersByFormat = testCaseExporters.stream()
                .collect(Collectors.toMap(TestCaseExporter::reportFormat, Function.identity(),
                        (first, _) -> {
                            throw new CatsException("Multiple report exporters registered for format " + first.reportFormat());
                        }, LinkedHashMap::new));
        this.reportingArguments = reportingArguments;
        resolveSelectedExporters();
    }

    private List<TestCaseExporter> resolveSelectedExporters() {
        List<ReportingArguments.ReportFormat> selectedFormats = reportingArguments.getReportFormat().stream()
                .distinct()
                .toList();
        if (selectedFormats.isEmpty()) {
            throw new CatsException("At least one report format must be selected");
        }
        return selectedFormats.stream()
                .map(format -> findExporter(exportersByFormat, format))
                .toList();
    }

    private TestCaseExporter resolvePrimaryExporter() {
        return resolveSelectedExporters().getFirst();
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
        reportingArguments.resetSensitiveData();
        List<TestCaseExporter> currentExporters = resolveSelectedExporters();
        TestCaseExporter currentPrimary = currentExporters.getFirst();
        currentPrimary.initPath(folder);
        for (TestCaseExporter exporter : currentExporters) {
            if (exporter != currentPrimary) {
                if (currentPrimary.getReportingPath() != null) {
                    exporter.shareReportingPath(currentPrimary.getReportingPath(), currentPrimary.getT0());
                } else {
                    exporter.initPath(folder);
                }
            }
        }
    }

    /**
     * Writes a format-independent test case artifact using the explicitly selected primary exporter.
     *
     * @param catsTestCase the test case to be written to the report
     */
    public void writeTestCase(CatsTestCase catsTestCase) {
        SensitiveDataPolicy.sanitize(catsTestCase, reportingArguments);
        List<TestCaseExporter> currentExporters = resolveSelectedExporters();
        TestCaseExporter currentPrimary = currentExporters.getFirst();
        TestCaseExporter writerExporter = currentExporters.stream()
                .filter(exp -> exp.reportFormat() == ReportingArguments.ReportFormat.HTML_JS
                        || exp.reportFormat() == ReportingArguments.ReportFormat.HTML_ONLY
                        || exp.reportFormat() == ReportingArguments.ReportFormat.HTML_JS_CLUSTERS)
                .findFirst()
                .orElse(currentPrimary);
        writerExporter.writeTestCase(catsTestCase);
    }

    /**
     * Writes helper files for the test case exporters.
     */
    public void writeHelperFiles() {
        resolveSelectedExporters().forEach(TestCaseExporter::writeHelperFiles);
    }

    /**
     * Writes a summary of the test case execution details.
     *
     * @param testCaseSummaryDetails the list of test case summaries to be written
     * @param executionSummary the single execution snapshot shared by every exporter
     */
    public void writeSummary(List<CatsTestCaseSummary> testCaseSummaryDetails, ExecutionSummary executionSummary) {
        resolveSelectedExporters().forEach(testCaseExporter ->
                testCaseExporter.writeSummary(testCaseSummaryDetails, executionSummary));
    }

    /**
     * Writes errors by reason for the provided test case summary details.
     *
     * @param testCaseSummaryDetails the list of test case summaries containing error details
     */
    public void writeErrorsByReason(List<CatsTestCaseSummary> testCaseSummaryDetails) {
        resolvePrimaryExporter().writeErrorsByReason(testCaseSummaryDetails);
    }


    /**
     * Writes a performance report based on the provided test case execution details.
     *
     * @param testCaseExecutionDetails the list of test case execution summaries to be included in the performance report
     */
    public void writePerformanceReport(List<CatsTestCaseExecutionSummary> testCaseExecutionDetails) {
        resolvePrimaryExporter().writePerformanceReport(testCaseExecutionDetails);
    }

    /**
     * Prints execution details through the explicitly selected primary exporter.
     */
    public void printExecutionDetails(ExecutionSummary executionSummary) {
        resolvePrimaryExporter().printExecutionDetails(executionSummary);
    }

    public void writeTopFuzzers(List<CatsTestCaseSummary> testCaseSummaries) {
        resolvePrimaryExporter().writeTopFuzzers(testCaseSummaries);
    }
}
