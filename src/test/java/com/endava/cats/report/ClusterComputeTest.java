package com.endava.cats.report;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ClusterComputeTest {

    @Test
    void testCreateClustersWithDifferentResultReasons() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                createTestCaseSummary(404, "Resource Missing", "error", "Error: Resource not available")
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0).get("resultReason")).isIn("Not Found", "Resource Missing");
        assertThat(clusters.get(1).get("resultReason")).isIn("Not Found", "Resource Missing");
    }

    @Test
    void testCreateClustersWithWarningsCases() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(400, "Bad Request", "warning", "Warning: Potential data inconsistency"),
                createTestCaseSummary(400, "Bad Request", "warning", "Warning: Potential data inconsistency")
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst()).containsEntry("resultReason", "Bad Request");
    }

    @Test
    void testCreateClustersClusterProperties() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /another/path")
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        Map<String, Object> cluster = ((List<Map<String, Object>>) clusters.get(0).get("clusters")).getFirst();

        assertThat(cluster).containsKeys("clusterId", "errorMessage", "borderColor", "paths");
        assertThat(cluster.get("paths")).isInstanceOf(List.class);
    }

    @Test
    void testCreateClustersWithMixedSuccessAndErrorCases() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(200, "OK", "success", "Success"),
                createTestCaseSummary(404, "Not Found", "error", "Error: Resource not found"),
                createTestCaseSummary(500, "Server Error", "error", "Error: Internal server error")
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(2);  // One for "Not Found", one for "Server Error"
    }

    @Test
    void testCreateClustersWithComplexErrorScenarios() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(400, "Bad Request", "error", "Error: Invalid input"),
                createTestCaseSummary(400, "Bad Request", "error", "Error: Invalid input format"),
                createTestCaseSummary(404, "Not Found", "error", "Error: Resource missing"),
                createTestCaseSummary(500, "Server Error", "error", "Error: Database connection failed")
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(3);
        assertThat(clusters.stream().map(c -> c.get("resultReason")))
                .containsExactlyInAnyOrder("Bad Request", "Not Found", "Server Error");
    }

    @ParameterizedTest
    @MethodSource("provideTestCasesAndExpectedClusterSize")
    void testCreateClusters(List<CatsTestCaseSummary> testCases, int expectedClusterSize) {
        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);
        assertThat(clusters).hasSize(expectedClusterSize);
    }

    @ParameterizedTest
    @MethodSource("provideSimilarErrorTestCases")
    void testCreateClustersWithSimilarErrors(List<CatsTestCaseSummary> testCases, int expectedClusterCount) {
        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);
        assertThat(clusters).hasSize(expectedClusterCount);
    }

    private static Stream<Arguments> provideTestCasesAndExpectedClusterSize() {
        return Stream.of(
                Arguments.of(new ArrayList<CatsTestCaseSummary>(), 0),

                Arguments.of(
                        List.of(
                                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                                createTestCaseSummary(500, "Internal Server Error", "error", "Error: Unable to connect to database")
                        ),
                        2
                ),

                Arguments.of(
                        List.of(
                                createTestCaseSummary(200, "OK", "success", "Success"),
                                createTestCaseSummary(201, "Created", "success", "Resource created")
                        ),
                        0
                ),

                Arguments.of(
                        List.of(
                                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                                createTestCaseSummary(200, "OK", "success", "Success"),
                                createTestCaseSummary(500, "Internal Server Error", "error", "Error: Unable to connect to database")
                        ),
                        2
                ),

                Arguments.of(
                        List.of(
                                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                                createTestCaseSummary(500, "Internal Server Error", "error", "Error: Unable to connect to database")
                        ),
                        2
                ),

                Arguments.of(
                        List.of(
                                createTestCaseSummary(404, "Not Found", "error", ""),
                                createTestCaseSummary(500, "Internal Server Error", "error", "")
                        ),
                        2
                ),

                Arguments.of(
                        List.of(
                                createTestCaseSummary(404, "Not Found", "error", null),
                                createTestCaseSummary(500, "Internal Server Error", "error", null)
                        ),
                        2
                )
        );
    }

    private static Stream<Arguments> provideSimilarErrorTestCases() {
        return Stream.of(
                Arguments.of(
                        List.of(
                                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /another/path/to/file")
                        ),
                        1
                ),

                Arguments.of(
                        List.of(
                                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"),
                                createTestCaseSummary(500, "Internal Server Error", "error", "Error: Unable to connect to database")
                        ),
                        2
                )
        );
    }

    private static CatsTestCaseSummary createTestCaseSummary(int httpResponseCode, String resultReason, String result, String responseBody) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setResponse(CatsResponse.builder().responseCode(httpResponseCode).body(responseBody).build());
        testCase.setResult(result);
        testCase.setPath("/path/" + CatsUtil.random().nextInt(1000));
        testCase.setContractPath(testCase.getPath());
        testCase.setResultReason(resultReason);
        testCase.setTestId(CatsUtil.random().nextInt(2000) + "");
        return CatsTestCaseSummary.fromCatsTestCase(testCase);
    }
}