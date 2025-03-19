package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.util.CatsUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Utility class for clustering test cases based on error similarity.
 */
public class ClusterCompute {
    public static List<Map<String, Object>> createClusters(List<CatsTestCaseSummary> testCases) {
        List<CatsTestCaseSummary> non2xxCases = new ArrayList<>();
        for (CatsTestCaseSummary tc : testCases) {
            if ((tc.getHttpResponseCode() < 200 || tc.getHttpResponseCode() >= 300)
                    && StringUtils.isNotBlank(tc.getResultReason())
                    && (tc.getError() || tc.getWarning())) {
                non2xxCases.add(tc);
            }
        }

        Map<String, List<CatsTestCaseSummary>> groupedByReason = new HashMap<>();
        for (CatsTestCaseSummary tc : non2xxCases) {
            String reason = tc.getResultReason();
            List<CatsTestCaseSummary> list = groupedByReason.computeIfAbsent(reason, k -> new ArrayList<>());
            list.add(tc);
        }

        List<Map<String, Object>> finalResult = new ArrayList<>();
        int clusterCounter = 1;

        for (Map.Entry<String, List<CatsTestCaseSummary>> entry : groupedByReason.entrySet()) {
            String resultReason = entry.getKey();
            List<CatsTestCaseSummary> testCasesForReason = entry.getValue();

            List<Map<String, Object>> clusterList = new ArrayList<>();

            List<CatsTestCaseSummary> emptyResponseCases = new ArrayList<>();
            List<CatsTestCaseSummary> nonEmptyResponseCases = new ArrayList<>();

            for (CatsTestCaseSummary tc : testCasesForReason) {
                if (tc.getResponseBody() == null || tc.getResponseBody().trim().isEmpty()) {
                    emptyResponseCases.add(tc);
                } else {
                    nonEmptyResponseCases.add(tc);
                }
            }

            List<List<CatsTestCaseSummary>> similarityClusters =
                    clusterBySimilarity(nonEmptyResponseCases, ErrorSimilarityDetector::areErrorsSimilar);

            if (!emptyResponseCases.isEmpty()) {
                similarityClusters.add(emptyResponseCases);
            }

            for (List<CatsTestCaseSummary> cluster : similarityClusters) {
                Map<String, Object> clusterMap = new HashMap<>();
                clusterMap.put("clusterId", clusterCounter++);
                String representativeError = cluster.getFirst().getResponseBody();

                clusterMap.put("errorMessage", representativeError);
                clusterMap.put("borderColor", getRandomHexColor());


                List<Map<String, Object>> pathList = getMapList(cluster);

                clusterMap.put("paths", pathList);
                clusterList.add(clusterMap);
            }

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("resultReason", resultReason);
            resultMap.put("clusters", clusterList);
            finalResult.add(resultMap);
        }

        return finalResult;
    }

    private static List<Map<String, Object>> getMapList(List<CatsTestCaseSummary> cluster) {
        Map<String, StringBuilder> pathGroups = getStringStringBuilderMap(cluster);

        List<Map<String, Object>> pathList = new ArrayList<>();
        for (Map.Entry<String, StringBuilder> pathEntry : pathGroups.entrySet()) {
            Map<String, Object> pathMap = new HashMap<>();
            pathMap.put("path", pathEntry.getKey());
            pathMap.put("testCases", pathEntry.getValue().toString());
            pathList.add(pathMap);
        }
        return pathList;
    }

    private static Map<String, StringBuilder> getStringStringBuilderMap(List<CatsTestCaseSummary> cluster) {
        Map<String, StringBuilder> pathGroups = new HashMap<>();
        for (CatsTestCaseSummary tc : cluster) {
            String path = tc.getPath();
            StringBuilder sb = pathGroups.get(path);
            if (sb == null) {
                sb = new StringBuilder();
                pathGroups.put(path, sb);
            } else {
                sb.append(", ");
            }
            sb.append(String.format("<a href=\"%s.html\" target=\"_blank\">%s</a>",
                    tc.getKey(), tc.getId()));
        }
        return pathGroups;
    }

    private static List<List<CatsTestCaseSummary>> clusterBySimilarity(
            List<CatsTestCaseSummary> testCases, BiPredicate<String, String> similarityChecker) {

        List<List<CatsTestCaseSummary>> clusters = new ArrayList<>();

        if (testCases.size() <= 1) {
            if (!testCases.isEmpty()) {
                List<CatsTestCaseSummary> singleCluster = new ArrayList<>();
                singleCluster.add(testCases.getFirst());
                clusters.add(singleCluster);
            }
            return clusters;
        }

        Map<CatsTestCaseSummary, Boolean[]> comparisonTracker = new HashMap<>();

        for (CatsTestCaseSummary current : testCases) {
            boolean addedToCluster = false;

            if (current.getResponseBody() == null || current.getResponseBody().trim().isEmpty()) {
                continue;
            }

            for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {
                List<CatsTestCaseSummary> cluster = clusters.get(clusterIndex);

                CatsTestCaseSummary representative = cluster.getFirst();

                Boolean[] comparisons = comparisonTracker.get(current);
                if (comparisons != null && comparisons.length > clusterIndex && comparisons[clusterIndex] != null) {
                    if (comparisons[clusterIndex]) {
                        cluster.add(current);
                        addedToCluster = true;
                        break;
                    }
                    continue;
                }

                boolean similar = similarityChecker.test(representative.getResponseBody(), current.getResponseBody());

                if (comparisons == null) {
                    comparisons = new Boolean[clusters.size()];
                    comparisonTracker.put(current, comparisons);
                } else if (comparisons.length <= clusterIndex) {
                    Boolean[] newComparisons = new Boolean[clusters.size()];
                    System.arraycopy(comparisons, 0, newComparisons, 0, comparisons.length);
                    comparisons = newComparisons;
                    comparisonTracker.put(current, comparisons);
                }
                comparisons[clusterIndex] = similar;

                if (similar) {
                    cluster.add(current);
                    addedToCluster = true;
                    break;
                }
            }

            if (!addedToCluster) {
                List<CatsTestCaseSummary> newCluster = new ArrayList<>();
                newCluster.add(current);
                clusters.add(newCluster);
            }
        }

        return clusters;
    }

    private static String getRandomHexColor() {
        int r = CatsUtil.random().nextInt(256);
        int g = CatsUtil.random().nextInt(256);
        int b = CatsUtil.random().nextInt(256);
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
