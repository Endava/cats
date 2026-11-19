package com.endava.cats.report.wfc;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.ProcessingError;
import com.webfuzzing.commons.VersionNumbers;
import com.webfuzzing.commons.faults.DefinedFaultCategory;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a schema-compliant Web Fuzzing Commons ({@link Report}) from CATS execution results.
 */
public final class WfcReportBuilder {
    private static final String TOOL_NAME = "CATS";
    private static final Set<Integer> UNDECLARED_METHOD_ALLOWED_FAULT_CODES = Set.of(
            DefinedFaultCategory.HTTP_STATUS_NO_405_IF_NO_ALLOW.getCode(),
            DefinedFaultCategory.SCHEMA_INVALID_ALLOW.getCode(),
            DefinedFaultCategory.SECURITY_HIDDEN_ACCESSIBLE_ENDPOINT.getCode());

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
                               Collection<ProcessingError> processingErrors,
                               Path reportingPath,
                               String toolVersion,
                               long executionTimeSeconds) {
        List<CatsTestCaseSummary> sortedSummaries = Optional.ofNullable(summaries).orElse(List.of())
                .stream()
                .sorted()
                .toList();

        Set<String> documentedEndpoints = collectDocumentedEndpointIds(openAPI);
        BuildAccumulator acc = new BuildAccumulator(
                documentedEndpoints,
                !documentedEndpoints.isEmpty(),
                new LinkedHashSet<>(documentedEndpoints),
                collectPrimaryOperationByPath(openAPI),
                OperationAuthContractIndex.from(openAPI),
                new LinkedHashSet<>(),
                new ArrayList<>(sortedSummaries.size()),
                new ArrayList<>(sortedSummaries.size()),
                new ArrayList<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>());

        for (CatsTestCaseSummary summary : sortedSummaries) {
            processSummary(summary, reportingPath, acc);
        }

        RESTReport restReport = new RESTReport();
        restReport.setEndpointIds(acc.endpointIds());
        restReport.setOutputHttpCalls(sortedSummaries.size());
        long attempted = executionSummary != null ? executionSummary.requestsAttempted() : sortedSummaries.size();
        restReport.setEvaluatedHttpCalls((int) Math.max(sortedSummaries.size(), attempted));
        restReport.setCoveredHttpStatus(acc.coveredEndpoints());

        ProblemDetails problemDetails = new ProblemDetails();
        problemDetails.setRest(restReport);

        Faults faults = new Faults();
        faults.setTotalNumber(acc.uniqueFaultKeys().size());
        faults.setFoundFaults(acc.foundFaults());

        Report report = new Report();
        report.setSchemaVersion(VersionNumbers.REPORT);
        report.setToolName(TOOL_NAME);
        report.setToolVersion(StringUtils.defaultIfBlank(toolVersion, "1.0.0"));
        report.setCreationTime(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        report.setFaults(faults);
        report.setProblemDetails(problemDetails);
        report.setTotalTests(sortedSummaries.size());
        report.setTestFilePaths(acc.testFilePaths());
        report.setTestCases(acc.wfcTestCases());
        report.setExecutionTimeInSeconds((int) Math.max(0, executionTimeSeconds));
        report.setExtra(buildExtraCoverage(acc.endpointIds().size(), acc.exercisedEndpoints().size(), acc.covered2xxEndpoints().size()));
        report.setWarnings(buildWarnings(processingErrors));
        return report;
    }

    private static void processSummary(CatsTestCaseSummary summary, Path reportingPath, BuildAccumulator acc) {
        String testKey = summary.getKey();
        String relativeJsonFile = testKey + ".json";
        TestFileMetadata fileMeta = inspectTestFile(reportingPath, relativeJsonFile);
        acc.testFilePaths().add(relativeJsonFile);
        acc.wfcTestCases().add(buildWfcTestCase(summary, testKey, relativeJsonFile, fileMeta.lineCount()));

        String operationId = toOperationId(summary.getHttpMethod(), summary.getPath());
        boolean isDocumented = !acc.hasDocumentedContract() || acc.documentedEndpoints().contains(operationId);
        if (!acc.hasDocumentedContract()) {
            acc.endpointIds().add(operationId);
        }
        if (isDocumented) {
            recordDocumentedCoverage(summary, operationId, testKey, acc);
        }

        String faultOperationId = resolveFaultOperationId(summary, operationId, isDocumented, acc.primaryOperationByPath());
        if (faultOperationId != null) {
            recordClassifiedFaults(summary, fileMeta.parsedTestCase(), operationId, faultOperationId, testKey, isDocumented, acc);
        }
    }

    private static void recordDocumentedCoverage(CatsTestCaseSummary summary, String operationId,
                                                 String testKey, BuildAccumulator acc) {
        acc.exercisedEndpoints().add(operationId);
        int statusCode = summary.getHttpResponseCode();
        if (ResponseCodeFamily.is2xxCode(statusCode)) {
            acc.covered2xxEndpoints().add(operationId);
        }
        acc.coveredEndpoints().add(buildCoveredEndpoint(operationId, testKey, statusCode));
    }

    private static void recordClassifiedFaults(CatsTestCaseSummary summary, CatsTestCase parsedTestCase,
                                               String operationId, String faultOperationId, String testKey,
                                               boolean isDocumented, BuildAccumulator acc) {
        OperationAuthContract authContract = isDocumented
                ? acc.authContracts().getOrDefault(operationId, OperationAuthContract.UNKNOWN)
                : OperationAuthContract.UNKNOWN;
        Set<FaultCategoryId> classifiedFaults = classifyForOperation(summary, parsedTestCase, authContract, isDocumented);
        if (classifiedFaults.isEmpty()) {
            return;
        }
        FoundFault foundFault = new FoundFault();
        foundFault.setOperationId(faultOperationId);
        foundFault.setTestCaseId(testKey);
        foundFault.setFaultCategories(classifiedFaults);
        acc.foundFaults().add(foundFault);

        for (FaultCategoryId fc : classifiedFaults) {
            acc.uniqueFaultKeys().add(faultOperationId + "|" + fc.getCode() + "|" + Optional.ofNullable(fc.getContext()).orElse(""));
        }
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

    private static Map<String, String> collectPrimaryOperationByPath(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null) {
            return Map.of();
        }
        Map<String, String> byPath = new LinkedHashMap<>();
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            String path = StringUtils.defaultIfBlank(entry.getKey(), "/").trim();
            PathItem item = entry.getValue();
            if (item == null || item.readOperationsMap() == null || item.readOperationsMap().isEmpty()) {
                continue;
            }
            PathItem.HttpMethod firstVerb = item.readOperationsMap().keySet().iterator().next();
            byPath.put(path, toOperationId(firstVerb.name(), path));
        }
        return Map.copyOf(byPath);
    }

    private static String resolveFaultOperationId(CatsTestCaseSummary summary, String operationId,
                                                  boolean isDocumentedOperation, Map<String, String> primaryOperationByPath) {
        if (isDocumentedOperation) {
            return operationId;
        }
        String path = StringUtils.defaultIfBlank(summary.getPath(), "/").trim();
        return primaryOperationByPath.get(path);
    }

    private static Set<FaultCategoryId> classifyForOperation(CatsTestCaseSummary summary, CatsTestCase parsedTestCase,
                                                             OperationAuthContract authContract, boolean isDocumentedOperation) {
        Set<FaultCategoryId> rawFaults = WfcFaultClassifier.classify(summary, parsedTestCase, authContract);
        if (isDocumentedOperation) {
            return rawFaults;
        }
        return rawFaults.stream()
                .filter(fc -> UNDECLARED_METHOD_ALLOWED_FAULT_CODES.contains(fc.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    private static List<Warning> buildWarnings(Collection<ProcessingError> processingErrors) {
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
            CatsTestCase parsed = CatsTestCase.readTestCaseFile(file).orElse(null);
            return new TestFileMetadata(lines, parsed);
        } catch (IOException | RuntimeException _) {
            return new TestFileMetadata(1, null);
        }
    }

    private record TestFileMetadata(int lineCount, CatsTestCase parsedTestCase) {
    }

    private record BuildAccumulator(
            Set<String> documentedEndpoints,
            boolean hasDocumentedContract,
            Set<String> endpointIds,
            Map<String, String> primaryOperationByPath,
            Map<String, OperationAuthContract> authContracts,
            Set<String> testFilePaths,
            List<TestCase> wfcTestCases,
            List<CoveredEndpoint> coveredEndpoints,
            List<FoundFault> foundFaults,
            Set<String> uniqueFaultKeys,
            Set<String> exercisedEndpoints,
            Set<String> covered2xxEndpoints) {
    }
}
