package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Utility class for clustering failing/warning test cases by root-cause error signature.
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
        Map<String, List<CatsTestCaseSummary>> groupedByReason = groupTestCasesByReason(reportableCases);
        return createClusterResultList(groupedByReason);
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
        Map<Integer, List<CatsTestCaseSummary>> byStatusCode = testCasesForReason.stream()
                .collect(Collectors.groupingBy(
                        CatsTestCaseSummary::getHttpResponseCode,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<List<CatsTestCaseSummary>> allClusters = new ArrayList<>();
        for (List<CatsTestCaseSummary> statusGroup : byStatusCode.values()) {
            List<CatsTestCaseSummary> emptyResponseCases = new ArrayList<>();
            List<CatsTestCaseSummary> nonEmptyResponseCases = new ArrayList<>();
            separateResponseCases(statusGroup, emptyResponseCases, nonEmptyResponseCases);

            allClusters.addAll(clusterBySimilarity(nonEmptyResponseCases, ErrorSimilarityDetector::areErrorsSimilar));
            if (!emptyResponseCases.isEmpty()) {
                allClusters.add(emptyResponseCases);
            }
        }

        allClusters.sort(Comparator.<List<CatsTestCaseSummary>>comparingInt(List::size).reversed());
        return createClusterMaps(allClusters);
    }

    private static void separateResponseCases(
            List<CatsTestCaseSummary> testCases,
            List<CatsTestCaseSummary> emptyResponseCases,
            List<CatsTestCaseSummary> nonEmptyResponseCases) {
        for (CatsTestCaseSummary tc : testCases) {
            if (ErrorSimilarityDetector.isBlankOrEmptyPlaceholder(tc.getResponseBody())) {
                emptyResponseCases.add(tc);
            } else {
                nonEmptyResponseCases.add(tc);
            }
        }
    }

    private static List<Map<String, Object>> createClusterMaps(
            List<List<CatsTestCaseSummary>> similarityClusters) {
        List<Map<String, Object>> clusterList = new ArrayList<>();
        int clusterCounter = 1;

        for (List<CatsTestCaseSummary> cluster : similarityClusters) {
            int currentId = clusterCounter++;
            CatsTestCaseSummary first = cluster.getFirst();
            Map<String, Object> clusterMap = new HashMap<>();
            clusterMap.put("clusterId", currentId);

            String representativeError = Optional.ofNullable(first.getResponseBody()).orElse("[Empty response body]");
            String rootCauseSummary = ErrorSimilarityDetector.extractRootCauseSummary(
                    first.getResponseBody(), first.getResultDetails());
            int statusCode = first.getHttpResponseCode();
            boolean hasError = cluster.stream().anyMatch(CatsTestCaseSummary::getError);
            boolean is5xx = statusCode >= 500 && statusCode < 600;

            Set<String> distinctFuzzers = cluster.stream()
                    .map(CatsTestCaseSummary::getFuzzer)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<Map<String, Object>> paths = createPathList(cluster);

            clusterMap.put("rootCauseSummary", rootCauseSummary);
            clusterMap.put("errorMessage", representativeError);
            clusterMap.put("httpStatusCode", statusCode);
            clusterMap.put("statusBadgeClass", statusBadgeClass(statusCode));
            clusterMap.put("severity", hasError ? "error" : "warning");
            clusterMap.put("is5xx", is5xx);
            clusterMap.put("testCount", cluster.size());
            clusterMap.put("pathCount", paths.size());
            clusterMap.put("fuzzerCount", distinctFuzzers.size());
            clusterMap.put("fuzzers", distinctFuzzers.isEmpty() ? "N/A" : String.join(", ", distinctFuzzers));
            clusterMap.put("borderColor", CLUSTER_PALETTE.get((currentId - 1) % CLUSTER_PALETTE.size()));
            clusterMap.put("paths", paths);
            clusterList.add(clusterMap);
        }

        return clusterList;
    }

    private static String statusBadgeClass(int code) {
        if (code >= 500 && code < 600) {
            return "badge-5xx";
        }
        if (code >= 400 && code < 500) {
            return "badge-4xx";
        }
        if (code >= 200 && code < 300) {
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

    private static List<List<CatsTestCaseSummary>> clusterBySimilarity(
            List<CatsTestCaseSummary> testCases,
            BiPredicate<String, String> similarityChecker) {
        if (testCases.isEmpty()) {
            return new ArrayList<>();
        }
        if (testCases.size() == 1) {
            return new ArrayList<>(List.of(new ArrayList<>(testCases)));
        }

        // Pass 1: Group by canonical normalized root-cause key in O(N)
        Map<String, List<CatsTestCaseSummary>> patternBuckets = new LinkedHashMap<>();
        for (CatsTestCaseSummary tc : testCases) {
            if (ErrorSimilarityDetector.isBlankOrEmptyPlaceholder(tc.getResponseBody())) {
                continue;
            }
            String key = ErrorSimilarityDetector.extractNormalizedClusterKey(
                    tc.getResponseBody(), tc.getResultDetails());
            patternBuckets.computeIfAbsent(key, _ -> new ArrayList<>()).add(tc);
        }

        if (patternBuckets.size() <= 1) {
            return new ArrayList<>(patternBuckets.values());
        }

        // Pass 2: Merge buckets whose representative error messages are similar across buckets
        List<List<CatsTestCaseSummary>> mergedClusters = new ArrayList<>();
        for (List<CatsTestCaseSummary> bucket : patternBuckets.values()) {
            CatsTestCaseSummary bucketRep = bucket.getFirst();
            boolean merged = false;
            for (List<CatsTestCaseSummary> existingCluster : mergedClusters) {
                CatsTestCaseSummary existingRep = existingCluster.getFirst();
                if (similarityChecker.test(existingRep.getResponseBody(), bucketRep.getResponseBody())) {
                    existingCluster.addAll(bucket);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                mergedClusters.add(new ArrayList<>(bucket));
            }
        }
        return mergedClusters;
    }
}
