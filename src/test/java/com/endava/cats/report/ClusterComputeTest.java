package com.endava.cats.report;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
    }

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
        Map<String, Object> cluster = ((List<Map<String, Object>>) clusters.getFirst().get("clusters")).getFirst();

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

    @Test
    void testCreateClustersWithLargeNumberOfSimilarErrors() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            testCases.add(createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/" + i));
        }

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) clusters.getFirst().get("clusters");
        assertThat(clusterList).hasSize(1);
        assertThat(clusterList.getFirst().get("paths")).isInstanceOf(List.class);
    }

    @Test
    void testCreateClustersWithMultiplePatternGroups() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            testCases.add(createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/" + i));
        }
        for (int i = 0; i < 10; i++) {
            testCases.add(createTestCaseSummary(404, "Not Found", "error", "Warning: Resource unavailable for id " + i));
        }
        for (int i = 0; i < 10; i++) {
            testCases.add(createTestCaseSummary(404, "Not Found", "error", "Exception: Database connection timeout after " + i + " seconds"));
        }

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) clusters.getFirst().get("clusters");
        assertThat(clusterList).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void testCreateClustersWithVeryLongErrorMessages() {
        String longError1 = "Error: " + "A".repeat(300) + " occurred at line 123";
        String longError2 = "Error: " + "A".repeat(300) + " occurred at line 456";
        String longError3 = "Error: " + "B".repeat(300) + " occurred at line 789";

        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(500, "Server Error", "error", longError1),
                createTestCaseSummary(500, "Server Error", "error", longError2),
                createTestCaseSummary(500, "Server Error", "error", longError3)
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) clusters.getFirst().get("clusters");
        assertThat(clusterList).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testCreateClustersWithSingleTestCaseInGroup() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(404, "Not Found", "error", "Error: Unique error message")
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) clusters.getFirst().get("clusters");
        assertThat(clusterList).hasSize(1);
    }

    @Test
    void testCreateClustersWithEmptyResponseBodies() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(404, "Not Found", "error", ""),
                createTestCaseSummary(404, "Not Found", "error", ""),
                createTestCaseSummary(404, "Not Found", "error", "   ")
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) clusters.getFirst().get("clusters");
        assertThat(clusterList).hasSize(1);
    }

    @Test
    void testCreateClustersWithMixedEmptyAndNonEmptyResponses() {
        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(404, "Not Found", "error", "Error: File not found"),
                createTestCaseSummary(404, "Not Found", "error", ""),
                createTestCaseSummary(404, "Not Found", "error", null),
                createTestCaseSummary(404, "Not Found", "error", "Error: File not found at different location")
        );

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) clusters.getFirst().get("clusters");
        assertThat(clusterList).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testCreateClustersWithDifferentPathsSameError() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            CatsTestCaseSummary tc = createTestCaseSummary(404, "Not Found", "error", "Error: Resource not found");
            testCases.add(tc);
        }

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) clusters.getFirst().get("clusters");
        assertThat(clusterList).hasSize(1);
        Map<String, Object> cluster = clusterList.getFirst();
        List<Map<String, Object>> paths = (List<Map<String, Object>>) cluster.get("paths");
        assertThat(paths).isNotEmpty();
    }

    @Test
    void testCreateClustersWithComparisonTrackerGrowth() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            testCases.add(createTestCaseSummary(404, "Not Found", "error", "Error type " + (i % 5) + ": details " + i));
        }

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) clusters.getFirst().get("clusters");
        assertThat(clusterList).isNotEmpty();
    }

    @Test
    void shouldSeparateDifferent500JsonExceptionsAndGroupIdenticalExceptionCauses() {
        String jacksonError1 = "{\"type\":\"http://localhost:8080/petclinic/api/owners/1\",\"title\":\"HttpMessageNotReadableException\",\"status\":500,\"detail\":\"JSON parse error: Cannot construct instance of OwnerDto\"}";
        String jacksonError2 = "{\"type\":\"http://localhost:8080/petclinic/api/pets/14\",\"title\":\"HttpMessageNotReadableException\",\"status\":500,\"detail\":\"JSON parse error: Cannot construct instance of OwnerDto\"}";
        String typeMismatch1 = "{\"type\":\"http://localhost:8080/petclinic/api/owners/-9223372036854775808\",\"title\":\"MethodArgumentTypeMismatchException\",\"status\":500,\"detail\":\"Failed to convert value of type 'java.lang.String' to required type 'int'; For input string: '-9223372036854775808'\"}";
        String typeMismatch2 = "{\"type\":\"http://localhost:8080/petclinic/api/pets/2147483648\",\"title\":\"MethodArgumentTypeMismatchException\",\"status\":500,\"detail\":\"Failed to convert value of type 'java.lang.String' to required type 'int'; For input string: '2147483648'\"}";
        String sqlIntegrity = "{\"type\":\"http://localhost:8080/petclinic/api/owners/1\",\"title\":\"DataIntegrityViolationException\",\"status\":500,\"detail\":\"could not execute statement [NULL not allowed for column 'TYPE_ID']\"}";

        List<CatsTestCaseSummary> testCases = List.of(
                createTestCaseSummary(500, "Unexpected response code: 500", "error", jacksonError1),
                createTestCaseSummary(500, "Unexpected response code: 500", "error", jacksonError2),
                createTestCaseSummary(500, "Unexpected response code: 500", "error", typeMismatch1),
                createTestCaseSummary(500, "Unexpected response code: 500", "error", typeMismatch2),
                createTestCaseSummary(500, "Unexpected response code: 500", "error", sqlIntegrity),
                createTestCaseSummary(200, "Unexpected response code: 200", "error", "{\"id\":1,\"status\":\"accepted\"}")
        );

        List<Map<String, Object>> groups = ClusterCompute.createClusters(testCases);

        // Both the 500 reason group and the 200 error reason group are included, sorted by count descending
        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst()).containsEntry("resultReason", "Unexpected response code: 500")
                .containsEntry("totalTests", 5);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clusters500 = (List<Map<String, Object>>) groups.getFirst().get("clusters");
        // The 5 500 responses should form 3 distinct root-cause clusters (2 Jackson, 2 TypeMismatch, 1 DataIntegrity)
        assertThat(clusters500).hasSize(3);
        assertThat(clusters500.get(0)).containsEntry("testCount", 2).containsEntry("httpStatusCode", 500);
        assertThat(clusters500.get(1)).containsEntry("testCount", 2).containsEntry("httpStatusCode", 500);
        assertThat(clusters500.get(2)).containsEntry("testCount", 1).containsEntry("httpStatusCode", 500);
        assertThat(String.valueOf(clusters500.get(2).get("rootCauseSummary")))
                .contains("DataIntegrityViolationException");
    }

    private static CatsTestCaseSummary createTestCaseSummary(int httpResponseCode, String resultReason, String result, String responseBody) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setRequest(com.endava.cats.model.CatsRequest.builder().httpMethod("POST").build());
        testCase.setFuzzer("TestFuzzer");
        testCase.setResponse(CatsResponse.builder().responseCode(httpResponseCode).body(responseBody).build());
        testCase.setResult(result);
        testCase.setPath("/path/" + CatsRandom.instance().nextInt(1000));
        testCase.setContractPath(testCase.getPath());
        testCase.setResultReason(resultReason);
        testCase.setTestId(CatsRandom.instance().nextInt(2000) + "");
        return CatsTestCaseSummary.fromCatsTestCase(testCase);
    }
}