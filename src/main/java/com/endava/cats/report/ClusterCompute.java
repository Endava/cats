package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.util.CatsUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Utility class for clustering test cases based on error similarity.
 */
public class ClusterCompute {

    private static final int MIN_HTTP_SUCCESS_CODE = 200;
    private static final int MAX_HTTP_SUCCESS_CODE = 300;

    private ClusterCompute() {
        // Prevent instantiation of utility class
    }

    /**
     * Creates clusters of test cases with similar errors.
     *
     * @param testCases List of test case summaries to cluster
     * @return List of clustered test case groups
     */
    public static List<Map<String, Object>> createClusters(List<CatsTestCaseSummary> testCases) {
        // Filter test cases with non-2xx responses and errors
        List<CatsTestCaseSummary> non2xxCases = filterNon2xxTestCases(testCases);

        // Group test cases by their result reason
        Map<String, List<CatsTestCaseSummary>> groupedByReason = groupTestCasesByReason(non2xxCases);

        return createClusterResultList(groupedByReason);
    }

    /**
     * Filters test cases with non-2xx HTTP responses and containing errors.
     *
     * @param testCases List of test cases to filter
     * @return Filtered list of test cases
     */
    private static List<CatsTestCaseSummary> filterNon2xxTestCases(List<CatsTestCaseSummary> testCases) {
        return testCases.stream()
                .filter(tc ->
                        (tc.getHttpResponseCode() < MIN_HTTP_SUCCESS_CODE || tc.getHttpResponseCode() >= MAX_HTTP_SUCCESS_CODE) &&
                                StringUtils.isNotBlank(tc.getResultReason()) &&
                                (tc.getError() || tc.getWarning())
                )
                .toList();
    }

    /**
     * Groups test cases by their result reason.
     *
     * @param testCases List of test cases to group
     * @return Map of test cases grouped by result reason
     */
    private static Map<String, List<CatsTestCaseSummary>> groupTestCasesByReason(List<CatsTestCaseSummary> testCases) {
        return testCases.stream()
                .collect(Collectors.groupingBy(CatsTestCaseSummary::getResultReason));
    }

    /**
     * Creates a list of clustered test case results.
     *
     * @param groupedByReason Map of test cases grouped by result reason
     * @return List of clustered test case groups
     */
    private static List<Map<String, Object>> createClusterResultList(
            Map<String, List<CatsTestCaseSummary>> groupedByReason) {

        return groupedByReason.entrySet().stream()
                .map(entry -> createResultMapForReason(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Creates a result map for a specific reason and its test cases.
     *
     * @param resultReason       Reason for the test cases
     * @param testCasesForReason List of test cases for this reason
     * @return Map representing the result
     */
    private static Map<String, Object> createResultMapForReason(
            String resultReason, List<CatsTestCaseSummary> testCasesForReason) {

        List<Map<String, Object>> clusterList = createClustersForTestCases(testCasesForReason);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("resultReason", resultReason);
        resultMap.put("clusters", clusterList);
        return resultMap;
    }

    /**
     * Creates clusters of test cases based on error similarity.
     *
     * @param testCasesForReason List of test cases to cluster
     * @return List of cluster maps
     */
    private static List<Map<String, Object>> createClustersForTestCases(
            List<CatsTestCaseSummary> testCasesForReason) {

        List<CatsTestCaseSummary> emptyResponseCases = new ArrayList<>();
        List<CatsTestCaseSummary> nonEmptyResponseCases = new ArrayList<>();

        // Separate empty and non-empty response cases
        separateResponseCases(testCasesForReason, emptyResponseCases, nonEmptyResponseCases);

        // Cluster similar error cases
        List<List<CatsTestCaseSummary>> similarityClusters =
                clusterBySimilarity(nonEmptyResponseCases, ErrorSimilarityDetector::areErrorsSimilar);

        // Add empty response cases to clusters if present
        if (!emptyResponseCases.isEmpty()) {
            similarityClusters.add(emptyResponseCases);
        }

        return createClusterMaps(similarityClusters);
    }

    /**
     * Separates test cases into empty and non-empty response cases.
     *
     * @param testCases             Source list of test cases
     * @param emptyResponseCases    Destination list for empty response cases
     * @param nonEmptyResponseCases Destination list for non-empty response cases
     */
    private static void separateResponseCases(
            List<CatsTestCaseSummary> testCases,
            List<CatsTestCaseSummary> emptyResponseCases,
            List<CatsTestCaseSummary> nonEmptyResponseCases) {

        for (CatsTestCaseSummary tc : testCases) {
            if (tc.getResponseBody() == null || tc.getResponseBody().trim().isEmpty()) {
                emptyResponseCases.add(tc);
            } else {
                nonEmptyResponseCases.add(tc);
            }
        }
    }

    /**
     * Creates cluster maps for given clusters of test cases.
     *
     * @param similarityClusters List of test case clusters
     * @return List of cluster maps
     */
    private static List<Map<String, Object>> createClusterMaps(
            List<List<CatsTestCaseSummary>> similarityClusters) {

        List<Map<String, Object>> clusterList = new ArrayList<>();
        int clusterCounter = 1;

        for (List<CatsTestCaseSummary> cluster : similarityClusters) {
            Map<String, Object> clusterMap = new HashMap<>();
            clusterMap.put("clusterId", clusterCounter++);

            String representativeError = cluster.getFirst().getResponseBody();
            clusterMap.put("errorMessage", representativeError);
            clusterMap.put("borderColor", generateRandomHexColor());

            clusterMap.put("paths", createPathList(cluster));
            clusterList.add(clusterMap);
        }

        return clusterList;
    }

    /**
     * Creates a list of path information for a cluster of test cases.
     *
     * @param cluster List of test cases in a cluster
     * @return List of path maps
     */
    private static List<Map<String, Object>> createPathList(List<CatsTestCaseSummary> cluster) {
        Map<String, StringBuilder> pathGroups = new HashMap<>();

        for (CatsTestCaseSummary tc : cluster) {
            String path = tc.getPath();
            pathGroups.computeIfAbsent(path, k -> new StringBuilder())
                    .append(pathGroups.get(path).length() > 0 ? ", " : "")
                    .append(String.format("<a href=\"%s.html\" target=\"_blank\">%s</a>",
                            tc.getKey(), tc.getId()));
        }

        return pathGroups.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> pathMap = new HashMap<>();
                    pathMap.put("path", entry.getKey());
                    pathMap.put("testCases", entry.getValue().toString());
                    return pathMap;
                })
                .toList();
    }

    /**
     * Clusters test cases by similarity using the provided similarity checker.
     *
     * @param testCases         List of test cases to cluster
     * @param similarityChecker Predicate to determine error similarity
     * @return List of test case clusters
     */
    private static List<List<CatsTestCaseSummary>> clusterBySimilarity(
            List<CatsTestCaseSummary> testCases,
            BiPredicate<String, String> similarityChecker) {

        if (testCases.isEmpty()) {
            return new ArrayList<>();
        }

        if (testCases.size() == 1) {
            return Collections.singletonList(testCases);
        }

        List<List<CatsTestCaseSummary>> clusters = new ArrayList<>();
        Map<CatsTestCaseSummary, Boolean[]> comparisonTracker = new HashMap<>();

        for (CatsTestCaseSummary current : testCases) {
            if (current.getResponseBody() == null || current.getResponseBody().trim().isEmpty()) {
                continue;
            }

            boolean addedToCluster = tryAddToExistingCluster(current, clusters, comparisonTracker, similarityChecker);

            if (!addedToCluster) {
                List<CatsTestCaseSummary> newCluster = new ArrayList<>();
                newCluster.add(current);
                clusters.add(newCluster);
            }
        }

        return clusters;
    }

    /**
     * Attempts to add a test case to an existing cluster based on similarity.
     *
     * @param current           Current test case to add
     * @param clusters          Existing clusters
     * @param comparisonTracker Tracker for previous comparisons
     * @param similarityChecker Predicate to determine error similarity
     * @return True if added to a cluster, false otherwise
     */
    private static boolean tryAddToExistingCluster(
            CatsTestCaseSummary current,
            List<List<CatsTestCaseSummary>> clusters,
            Map<CatsTestCaseSummary, Boolean[]> comparisonTracker,
            BiPredicate<String, String> similarityChecker) {

        for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {
            List<CatsTestCaseSummary> cluster = clusters.get(clusterIndex);
            CatsTestCaseSummary representative = cluster.getFirst();

            Boolean[] comparisons = getOrCreateComparisons(current, comparisonTracker, clusters.size());
            Boolean previousComparison = comparisons[clusterIndex];

            if (previousComparison != null) {
                if (previousComparison) {
                    cluster.add(current);
                    return true;
                }
                continue;
            }

            boolean similar = similarityChecker.test(representative.getResponseBody(), current.getResponseBody());
            comparisons[clusterIndex] = similar;

            if (similar) {
                cluster.add(current);
                return true;
            }
        }

        return false;
    }

    /**
     * Gets or creates comparison tracking array for a test case.
     *
     * @param current           Current test case
     * @param comparisonTracker Existing comparison tracker
     * @param clusterSize       Number of clusters
     * @return Comparison tracking array
     */
    private static Boolean[] getOrCreateComparisons(
            CatsTestCaseSummary current,
            Map<CatsTestCaseSummary, Boolean[]> comparisonTracker,
            int clusterSize) {

        Boolean[] comparisons = comparisonTracker.get(current);
        if (comparisons == null) {
            comparisons = new Boolean[clusterSize];
            comparisonTracker.put(current, comparisons);
        } else if (comparisons.length < clusterSize) {
            Boolean[] newComparisons = new Boolean[clusterSize];
            System.arraycopy(comparisons, 0, newComparisons, 0, comparisons.length);
            comparisons = newComparisons;
            comparisonTracker.put(current, comparisons);
        }
        return comparisons;
    }

    /**
     * Generates a random hex color.
     *
     * @return Random hex color string
     */
    private static String generateRandomHexColor() {
        int r = CatsUtil.random().nextInt(256);
        int g = CatsUtil.random().nextInt(256);
        int b = CatsUtil.random().nextInt(256);
        return String.format("#%02x%02x%02x", r, g, b);
    }
}