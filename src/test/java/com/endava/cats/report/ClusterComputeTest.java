package com.endava.cats.report;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterComputeTest {

    @Test
    void testCreateClusters_withEmptyTestCases() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).isEmpty();
    }

    @Test
    void testCreateClusters_withNon2xxTestCases() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        testCases.add(createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"));
        testCases.add(createTestCaseSummary(500, "Internal Server Error", "error", "Error: Unable to connect to database"));

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(2);
    }

    @Test
    void testCreateClusters_with2xxTestCases() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        testCases.add(createTestCaseSummary(200, "OK", "success", "Success"));
        testCases.add(createTestCaseSummary(201, "Created", "success", "Resource created"));

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).isEmpty();
    }

    @Test
    void testCreateClusters_withMixedTestCases() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        testCases.add(createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"));
        testCases.add(createTestCaseSummary(200, "OK", "success", "Success"));
        testCases.add(createTestCaseSummary(500, "Internal Server Error", "error", "Error: Unable to connect to database"));

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(2);
    }

    @Test
    void testCreateClusters_withSimilarErrors() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        testCases.add(createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"));
        testCases.add(createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /another/path/to/file"));

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().get("clusters")).asInstanceOf(InstanceOfAssertFactories.LIST).hasSize(1);
    }

    @Test
    void testCreateClusters_withDifferentErrors() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        testCases.add(createTestCaseSummary(404, "Not Found", "error", "Error: File not found at /path/to/file"));
        testCases.add(createTestCaseSummary(500, "Internal Server Error", "error", "Error: Unable to connect to database"));

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(2);
    }

    @Test
    void testCreateClusters_withEmptyResponseBodies() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        testCases.add(createTestCaseSummary(404, "Not Found", "error", ""));
        testCases.add(createTestCaseSummary(500, "Internal Server Error", "error", ""));

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(2);
    }

    @Test
    void testCreateClusters_withNullResponseBodies() {
        List<CatsTestCaseSummary> testCases = new ArrayList<>();
        testCases.add(createTestCaseSummary(404, "Not Found", "error", null));
        testCases.add(createTestCaseSummary(500, "Internal Server Error", "error", null));

        List<Map<String, Object>> clusters = ClusterCompute.createClusters(testCases);

        assertThat(clusters).hasSize(2);
    }

    private CatsTestCaseSummary createTestCaseSummary(int httpResponseCode, String resultReason, String result, String responseBody) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setResponse(CatsResponse.builder().responseCode(httpResponseCode).body(responseBody).build());
        testCase.setResult(result);
        testCase.setResultReason(resultReason);
        testCase.setTestId(CatsUtil.random().nextInt(2000) + "");
        return CatsTestCaseSummary.fromCatsTestCase(testCase);
    }
}