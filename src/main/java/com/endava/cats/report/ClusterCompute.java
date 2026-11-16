package com.endava.cats.report;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ErrorSignature;
import com.endava.cats.report.error.contract.ErrorResponseContract;
import com.endava.cats.report.error.contract.ErrorResponseContracts;
import com.endava.cats.report.error.extract.EmptyBody;
import com.endava.cats.report.error.extract.ErrorBodyExtraction;
import com.endava.cats.report.error.mask.ErrorSignatureMasker;
import com.endava.cats.report.error.template.ErrorSample;
import com.endava.cats.report.error.template.ErrorTemplate;
import com.endava.cats.report.error.template.ErrorTemplateMiner;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class for clustering failing/warning test cases by mined error template (see {@link ErrorTemplateMiner}).
 */
public class ClusterCompute {
    private static final List<String> CLUSTER_PALETTE = List.of(
            "#e63946", "#f4a261", "#2a9d8f", "#457b9d", "#8338ec", "#fb5607", "#3a86ff", "#06d6a0");

    private ClusterCompute() {
        // Prevent instantiation of utility class
    }

    /**
     * Creates clusters of test cases with similar errors/warnings grouped by result reason.
     *
     * @param testCases List of test case summaries to cluster
     * @return List of clustered test case groups sorted by impact
     */
    public static List<Map<String, Object>> createClusters(List<CatsTestCaseSummary> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            return List.of();
        }
        List<CatsTestCaseSummary> reportableCases = filterReportableTestCases(testCases);
        assignErrorTemplates(reportableCases);
        Map<String, List<CatsTestCaseSummary>> groupedByReason = groupTestCasesByReason(reportableCases);
        return createClusterResultList(groupedByReason);
    }

    /**
     * Mines and assigns an error template to every error and warning test case, partitioned by result reason. Does
     * nothing when all of them already carry a template, e.g. summaries read back from {@code cats-summary-report.json}.
     *
     * @param testCases the test case summaries of a run
     */
    public static void assignErrorTemplates(List<CatsTestCaseSummary> testCases) {
        assignErrorTemplates(testCases, null);
    }

    /**
     * Mines and assigns an error template to every error and warning test case, reading the error bodies by the field
     * roles the contract declares for them. Does nothing when all of them already carry a template.
     *
     * @param testCases the test case summaries of a run
     * @param openAPI   the contract of the run, or {@code null} to read the error bodies by field name only
     */
    public static void assignErrorTemplates(List<CatsTestCaseSummary> testCases, OpenAPI openAPI) {
        List<CatsTestCaseSummary> reportableCases = filterReportableTestCases(Optional.ofNullable(testCases).orElse(List.of()));
        if (reportableCases.stream().map(CatsTestCaseSummary::getErrorTemplateId).allMatch(Objects::nonNull)) {
            return;
        }
        List<ErrorTemplate> templates = ErrorTemplateMiner.mine(reportableCases.stream().map(testCase -> errorSample(testCase, openAPI)).toList());
        IntStream.range(0, reportableCases.size())
                .forEach(index -> reportableCases.get(index).assignErrorTemplate(templates.get(index).id(), templates.get(index).text()));
    }

    private static ErrorSample errorSample(CatsTestCaseSummary testCase, OpenAPI openAPI) {
        return new ErrorSample(testCase.getResultReason(), testCase.getHttpResponseCode(), testCase.getErrorSignature()
                .orElseGet(() -> signatureOf(testCase, openAPI)));
    }

    private static ErrorSignature signatureOf(CatsTestCaseSummary testCase, OpenAPI openAPI) {
        ErrorResponseContract declaredBody = ErrorResponseContracts.lookup(openAPI, testCase.getHttpMethod(), testCase.getPath(),
                testCase.getHttpResponseCode());
        return ErrorSignatureMasker.signatureOf(testCase.getResponseBody(), null, testCase.getRequestVocabulary(), declaredBody);
    }

    private static List<CatsTestCaseSummary> filterReportableTestCases(List<CatsTestCaseSummary> testCases) {
        return testCases.stream()
                .filter(tc -> tc != null
                        && StringUtils.isNotBlank(tc.getResultReason())
                        && (tc.getError() || tc.getWarning()))
                .toList();
    }

    private static Map<String, List<CatsTestCaseSummary>> groupTestCasesByReason(List<CatsTestCaseSummary> testCases) {
        return testCases.stream()
                .collect(Collectors.groupingBy(
                        CatsTestCaseSummary::getResultReason,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private static List<Map<String, Object>> createClusterResultList(
            Map<String, List<CatsTestCaseSummary>> groupedByReason) {
        return groupedByReason.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, List<CatsTestCaseSummary>>>comparingLong(
                                e -> e.getValue().stream().filter(CatsTestCaseSummary::getError).count())
                        .reversed()
                        .thenComparing(Comparator
                                .<Map.Entry<String, List<CatsTestCaseSummary>>>comparingInt(e -> e.getValue().size())
                                .reversed())
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> createResultMapForReason(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Map<String, Object> createResultMapForReason(
            String resultReason, List<CatsTestCaseSummary> testCasesForReason) {
        List<Map<String, Object>> clusterList = createClustersForTestCases(testCasesForReason);
        long errorCount = testCasesForReason.stream().filter(CatsTestCaseSummary::getError).count();
        long warningCount = testCasesForReason.stream().filter(CatsTestCaseSummary::getWarning).count();

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("resultReason", resultReason);
        resultMap.put("totalTests", testCasesForReason.size());
        resultMap.put("errorCount", errorCount);
        resultMap.put("warningCount", warningCount);
        resultMap.put("hasErrors", errorCount > 0);
        resultMap.put("hasWarnings", warningCount > 0);
        resultMap.put("clusterCount", clusterList.size());
        resultMap.put("severityClass", errorCount > 0 ? "reason-error" : "reason-warning");
        resultMap.put("clusters", clusterList);
        return resultMap;
    }

    private static List<Map<String, Object>> createClustersForTestCases(
            List<CatsTestCaseSummary> testCasesForReason) {
        Map<String, List<CatsTestCaseSummary>> byTemplate = testCasesForReason.stream()
                .collect(Collectors.groupingBy(CatsTestCaseSummary::getErrorTemplateId, LinkedHashMap::new, Collectors.toList()));
        List<List<CatsTestCaseSummary>> clusters = byTemplate.values().stream()
                .sorted(Comparator.<List<CatsTestCaseSummary>>comparingInt(List::size).reversed())
                .toList();
        return IntStream.range(0, clusters.size())
                .mapToObj(index -> createClusterMap(index + 1, clusters.get(index)))
                .toList();
    }

    private static Map<String, Object> createClusterMap(int clusterId, List<CatsTestCaseSummary> cluster) {
        Map<String, Object> clusterMap = new HashMap<>(rootCauseEntries(cluster.getFirst()));
        clusterMap.putAll(compositionEntries(cluster));
        clusterMap.put("clusterId", clusterId);
        clusterMap.put("severity", cluster.stream().anyMatch(CatsTestCaseSummary::getError) ? "error" : "warning");
        clusterMap.put("borderColor", CLUSTER_PALETTE.get((clusterId - 1) % CLUSTER_PALETTE.size()));
        return clusterMap;
    }

    private static Map<String, Object> rootCauseEntries(CatsTestCaseSummary representative) {
        int statusCode = representative.getHttpResponseCode();
        return Map.of(
                "templateId", representative.getErrorTemplateId(),
                "errorTemplate", representative.getErrorTemplate(),
                "rootCauseSummary", ErrorBodyExtraction.rootCauseSummary(representative.getResponseBody(), representative.getResultDetails()),
                "errorMessage", Optional.ofNullable(representative.getResponseBody()).orElse(EmptyBody.PLACEHOLDER),
                "httpStatusCode", statusCode,
                "statusBadgeClass", statusBadgeClass(statusCode),
                "is5xx", ResponseCodeFamily.is5xxCode(statusCode));
    }

    private static Map<String, Object> compositionEntries(List<CatsTestCaseSummary> cluster) {
        Set<String> distinctFuzzers = cluster.stream()
                .map(CatsTestCaseSummary::getFuzzer)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> paths = createPathList(cluster);
        long baselineFailureCount = cluster.stream()
                .filter(tc -> StringUtils.isNotBlank(tc.getSameErrorAsBaselineTest()))
                .count();
        return Map.of(
                "testCount", cluster.size(),
                "pathCount", paths.size(),
                "fuzzerCount", distinctFuzzers.size(),
                "fuzzers", distinctFuzzers.isEmpty() ? "N/A" : String.join(", ", distinctFuzzers),
                "baselineFailureCount", baselineFailureCount,
                "hasBaselineFailures", baselineFailureCount > 0,
                "paths", paths);
    }

    private static String statusBadgeClass(int code) {
        if (ResponseCodeFamily.is5xxCode(code)) {
            return "badge-5xx";
        }
        if (ResponseCodeFamily.is4xxCode(code)) {
            return "badge-4xx";
        }
        if (ResponseCodeFamily.is2xxCode(code)) {
            return "badge-2xx";
        }
        return "badge-other";
    }

    private static List<Map<String, Object>> createPathList(List<CatsTestCaseSummary> cluster) {
        Map<String, List<CatsTestCaseSummary>> byPath = cluster.stream()
                .collect(Collectors.groupingBy(
                        tc -> Optional.ofNullable(tc.getPath()).orElse("N/A"),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return byPath.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<CatsTestCaseSummary>>>comparingInt(e -> e.getValue().size()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> {
                    List<CatsTestCaseSummary> casesForPath = entry.getValue();
                    String links = casesForPath.stream()
                            .map(tc -> String.format("<a class=\"cluster-test-pill\" href=\"%s.html\" target=\"_blank\">%s</a>",
                                    tc.getKey(), tc.getId()))
                            .collect(Collectors.joining(", "));
                    Set<String> methods = casesForPath.stream()
                            .map(CatsTestCaseSummary::getHttpMethod)
                            .filter(StringUtils::isNotBlank)
                            .map(m -> m.toUpperCase(Locale.ROOT))
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    Map<String, Object> pathMap = new HashMap<>();
                    pathMap.put("path", entry.getKey());
                    pathMap.put("methods", methods.isEmpty() ? "" : String.join("/", methods));
                    pathMap.put("pathTestCount", casesForPath.size());
                    pathMap.put("testCases", links);
                    return pathMap;
                })
                .toList();
    }
}
