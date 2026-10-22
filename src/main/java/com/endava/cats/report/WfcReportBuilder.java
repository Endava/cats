package com.endava.cats.report;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.ProcessingError;
import com.endava.cats.util.JsonUtils;
import com.webfuzzing.commons.VersionNumbers;
import com.webfuzzing.commons.report.Coverage;
import com.webfuzzing.commons.report.CoverageCriterion;
import com.webfuzzing.commons.report.CoveredEndpoint;
import com.webfuzzing.commons.report.FaultCategoryId;
import com.webfuzzing.commons.report.Faults;
import com.webfuzzing.commons.report.FoundFault;
import com.webfuzzing.commons.report.ProblemDetails;
import com.webfuzzing.commons.report.RESTReport;
import com.webfuzzing.commons.report.Report;
import com.webfuzzing.commons.report.TestCase;
import com.webfuzzing.commons.report.Warning;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a schema-compliant Web Fuzzing Commons ({@link Report}) from CATS execution results.
 */
public final class WfcReportBuilder {
    private static final String TOOL_NAME = "CATS";

    private WfcReportBuilder() {
        // Utility class
    }

    /**
     * Constructs a WFC {@link Report} instance populated with faults, REST endpoint coverage, test cases,
     * warnings, and extra coverage metrics.
     */
    public static Report build(List<CatsTestCaseSummary> summaries,
                               ExecutionSummary executionSummary,
                               OpenAPI openAPI,
                               java.util.Collection<ProcessingError> processingErrors,
                               Path reportingPath,
                               String toolVersion,
                               long executionTimeSeconds) {
        List<CatsTestCaseSummary> sortedSummaries = Optional.ofNullable(summaries).orElse(List.of())
                .stream()
                .sorted()
                .toList();

        Set<String> endpointIds = collectDocumentedEndpointIds(openAPI);
        Set<String> testFilePaths = new LinkedHashSet<>();
        List<TestCase> wfcTestCases = new ArrayList<>(sortedSummaries.size());
        List<CoveredEndpoint> coveredEndpoints = new ArrayList<>(sortedSummaries.size());
        List<FoundFault> foundFaults = new ArrayList<>();
        Set<String> uniqueFaultKeys = new LinkedHashSet<>();
        Set<String> exercisedEndpoints = new LinkedHashSet<>();
        Set<String> covered2xxEndpoints = new LinkedHashSet<>();

        for (CatsTestCaseSummary summary : sortedSummaries) {
            String testKey = summary.getKey();
            String relativeJsonFile = testKey + ".json";
            TestFileMetadata fileMeta = inspectTestFile(reportingPath, relativeJsonFile);
            testFilePaths.add(relativeJsonFile);

            String operationId = toOperationId(summary.getHttpMethod(), summary.getPath());
            endpointIds.add(operationId);
            exercisedEndpoints.add(operationId);

            int statusCode = summary.getHttpResponseCode();
            if (ResponseCodeFamily.is2xxCode(statusCode)) {
                covered2xxEndpoints.add(operationId);
            }

            wfcTestCases.add(buildWfcTestCase(summary, testKey, relativeJsonFile, fileMeta.lineCount()));
            coveredEndpoints.add(buildCoveredEndpoint(operationId, testKey, statusCode));

            Set<FaultCategoryId> classifiedFaults = WfcFaultClassifier.classify(summary, fileMeta.parsedTestCase());
            if (!classifiedFaults.isEmpty()) {
                FoundFault foundFault = new FoundFault();
                foundFault.setOperationId(operationId);
                foundFault.setTestCaseId(testKey);
                foundFault.setFaultCategories(classifiedFaults);
                foundFaults.add(foundFault);

                for (FaultCategoryId fc : classifiedFaults) {
                    uniqueFaultKeys.add(operationId + "|" + fc.getCode() + "|" + Optional.ofNullable(fc.getContext()).orElse(""));
                }
            }
        }

        RESTReport restReport = new RESTReport();
        restReport.setEndpointIds(endpointIds);
        restReport.setOutputHttpCalls(sortedSummaries.size());
        long attempted = executionSummary != null ? executionSummary.requestsAttempted() : sortedSummaries.size();
        restReport.setEvaluatedHttpCalls((int) Math.max(sortedSummaries.size(), attempted));
        restReport.setCoveredHttpStatus(coveredEndpoints);

        ProblemDetails problemDetails = new ProblemDetails();
        problemDetails.setRest(restReport);

        Faults faults = new Faults();
        faults.setTotalNumber(uniqueFaultKeys.size());
        faults.setFoundFaults(foundFaults);

        Report report = new Report();
        report.setSchemaVersion(VersionNumbers.REPORT);
        report.setToolName(TOOL_NAME);
        report.setToolVersion(StringUtils.defaultIfBlank(toolVersion, "1.0.0"));
        report.setCreationTime(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        report.setFaults(faults);
        report.setProblemDetails(problemDetails);
        report.setTotalTests(sortedSummaries.size());
        report.setTestFilePaths(testFilePaths);
        report.setTestCases(wfcTestCases);
        report.setExecutionTimeInSeconds((int) Math.max(0, executionTimeSeconds));
        report.setExtra(buildExtraCoverage(endpointIds.size(), exercisedEndpoints.size(), covered2xxEndpoints.size()));
        report.setWarnings(buildWarnings(processingErrors));
        return report;
    }

    static Set<String> collectDocumentedEndpointIds(OpenAPI openAPI) {
        Set<String> endpointIds = new LinkedHashSet<>();
        if (openAPI == null || openAPI.getPaths() == null) {
            return endpointIds;
        }
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem item = entry.getValue();
            if (item == null || item.readOperationsMap() == null) {
                continue;
            }
            item.readOperationsMap().keySet().forEach(verb ->
                    endpointIds.add(toOperationId(verb.name(), path)));
        }
        return endpointIds;
    }

    static String toOperationId(String httpMethod, String contractPath) {
        String verb = StringUtils.defaultIfBlank(httpMethod, HttpMethod.GET.name()).trim().toUpperCase(Locale.ROOT);
        String path = StringUtils.defaultIfBlank(contractPath, "/").trim();
        return verb + ":" + path;
    }

    private static TestCase buildWfcTestCase(CatsTestCaseSummary summary, String testKey,
                                             String relativeJsonFile, int lineCount) {
        TestCase tc = new TestCase();
        tc.setId(testKey);
        tc.setFilePath(relativeJsonFile);
        tc.setName("%s - %s (%s %s)".formatted(
                summary.getId(),
                StringUtils.defaultIfBlank(summary.getFuzzer(), "Fuzzer"),
                StringUtils.defaultIfBlank(summary.getHttpMethod(), HttpMethod.GET.name()).toUpperCase(Locale.ROOT),
                StringUtils.defaultIfBlank(summary.getPath(), "/")));
        tc.setStartLine(1);
        tc.setEndLine(Math.max(1, lineCount));
        return tc;
    }

    private static CoveredEndpoint buildCoveredEndpoint(String operationId, String testKey, int statusCode) {
        CoveredEndpoint covered = new CoveredEndpoint();
        covered.setEndpointId(operationId);
        covered.setTestCaseId(testKey);
        if (ResponseCodeFamily.isValidHttpCode(statusCode)) {
            covered.setHttpStatus(Set.of(statusCode));
        } else {
            covered.setHttpStatus(Collections.emptySet());
        }
        return covered;
    }

    private static List<Coverage> buildExtraCoverage(int totalEndpoints, int exercisedCount, int covered2xxCount) {
        CoverageCriterion exercised = new CoverageCriterion();
        exercised.setName("Documented Operations Exercised");
        exercised.setCovered(exercisedCount);
        exercised.setTotal(totalEndpoints);

        CoverageCriterion success2xx = new CoverageCriterion();
        success2xx.setName("Documented Operations Returning 2XX");
        success2xx.setCovered(covered2xxCount);
        success2xx.setTotal(totalEndpoints);

        Coverage coverage = new Coverage();
        coverage.setToolName(TOOL_NAME);
        coverage.setCriteria(List.of(exercised, success2xx));
        return List.of(coverage);
    }

    private static List<Warning> buildWarnings(java.util.Collection<ProcessingError> processingErrors) {
        if (processingErrors == null || processingErrors.isEmpty()) {
            return List.of();
        }
        List<Warning> warnings = new ArrayList<>(processingErrors.size());
        for (ProcessingError err : processingErrors) {
            Warning warning = new Warning();
            warning.setCategory("SCHEMA_OR_PROCESSING");
            warning.setDisplayPriority(1);
            warning.setMessage(err.toString());
            warnings.add(warning);
        }
        return warnings;
    }

    private static TestFileMetadata inspectTestFile(Path reportingPath, String relativeJsonFile) {
        if (reportingPath == null) {
            return new TestFileMetadata(1, null);
        }
        Path file = reportingPath.resolve(relativeJsonFile);
        if (!Files.isRegularFile(file)) {
            return new TestFileMetadata(1, null);
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int lines = Math.max(1, content.split("\r?\n", -1).length);
            CatsTestCase parsed = JsonUtils.GSON.fromJson(content, CatsTestCase.class);
            return new TestFileMetadata(lines, parsed);
        } catch (IOException | RuntimeException _) {
            return new TestFileMetadata(1, null);
        }
    }

    private record TestFileMetadata(int lineCount, CatsTestCase parsedTestCase) {
    }
}
