package com.endava.cats.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.report.wfc.WfcFaultClassifier;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

@QuarkusTest
class BaselineFailureRegistryTest {

    @Test
    void shouldAnnotateFuzzedTestMatchingBaselineOperationStatusAndSignature() {
        BaselineFailureRegistry registry = new BaselineFailureRegistry();
        CatsTestCase baseline = testCase("Test 3", "HappyPath", "error", "POST", "/orders", 500,
                "{\"customerId\":\"c-100\"}", "{\"message\":\"Customer 'c-100' does not exist\"}");
        CatsTestCase fuzzed = testCase("Test 12", "RemoveFields", "error", "POST", "/orders", 500,
                "{\"customerId\":\"c-100\",\"note\":\"x\"}", "{\"message\":\"Customer 'c-100' does not exist\"}");

        registry.recordOrAnnotate(baseline);
        registry.recordOrAnnotate(fuzzed);

        Assertions.assertThat(baseline.hasSameErrorAsBaseline()).isFalse();
        Assertions.assertThat(baseline.getSameErrorAsBaselineTest()).isNull();
        Assertions.assertThat(fuzzed.hasSameErrorAsBaseline()).isTrue();
        Assertions.assertThat(fuzzed.getSameErrorAsBaselineTest()).isEqualTo("Test 3");
        Assertions.assertThat(fuzzed.getSameErrorAsBaselineFuzzer()).isEqualTo("HappyPath");
        Assertions.assertThat(fuzzed.getSameErrorAsBaselineTestPage()).isEqualTo("Test3.html");
        Assertions.assertThat(fuzzed.getTestKey()).isEqualTo("Test12");
    }

    @Test
    void shouldNeverAnnotateSeedTestsEvenWhenAnotherSeedTestFailedFirst() {
        BaselineFailureRegistry registry = new BaselineFailureRegistry();
        CatsTestCase happyPath = testCase("Test 1", "HappyPathFuzzer", "error", "POST", "/orders", 500,
                "{}", "{\"message\":\"Database down\"}");
        CatsTestCase onlyRequired = testCase("Test 2", "OnlyRequiredFieldsFuzzer", "error", "POST", "/orders", 500,
                "{}", "{\"message\":\"Database down\"}");

        registry.recordOrAnnotate(happyPath);
        registry.recordOrAnnotate(onlyRequired);

        Assertions.assertThat(happyPath.getSameErrorAsBaselineTest()).isNull();
        Assertions.assertThat(onlyRequired.getSameErrorAsBaselineTest()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /orders, 500, {\"message\":\"Database down\"}",
            "POST, /payments, 500, {\"message\":\"Database down\"}",
            "POST, /orders, 502, {\"message\":\"Database down\"}",
            "POST, /orders, 500, {\"message\":\"NullPointerException in OrderService\"}"
    })
    void shouldNotAnnotateWhenOperationStatusOrSignatureDiffers(String method, String path, int status, String body) {
        BaselineFailureRegistry registry = new BaselineFailureRegistry();
        registry.recordOrAnnotate(testCase("Test 1", "HappyPath", "error", "POST", "/orders", 500,
                "{}", "{\"message\":\"Database down\"}"));

        CatsTestCase fuzzed = testCase("Test 9", "RemoveFields", "error", method, path, status, "{}", body);
        registry.recordOrAnnotate(fuzzed);

        Assertions.assertThat(fuzzed.getSameErrorAsBaselineTest()).isNull();
        Assertions.assertThat(fuzzed.getSameErrorAsBaselineFuzzer()).isNull();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL",
            "''",
            "'   '",
            "[Empty response body]"
    })
    void shouldNeverRecordOrAnnotateEmptyBodies(String emptyBody) {
        BaselineFailureRegistry registry = new BaselineFailureRegistry();
        CatsTestCase baseline = testCase("Test 1", "HappyPath", "error", "GET", "/orders", 500, "", emptyBody);
        CatsTestCase fuzzed = testCase("Test 2", "ExtraHeaders", "error", "GET", "/orders", 500, "", emptyBody);

        registry.recordOrAnnotate(baseline);
        registry.recordOrAnnotate(fuzzed);

        Assertions.assertThat(fuzzed.getSameErrorAsBaselineTest()).isNull();
    }

    @Test
    void shouldClearRecordedBaselineFailuresBetweenRuns() {
        BaselineFailureRegistry registry = new BaselineFailureRegistry();
        registry.recordOrAnnotate(testCase("Test 1", "HappyPath", "error", "POST", "/orders", 500,
                "{}", "{\"message\":\"Database down\"}"));
        registry.clear();

        CatsTestCase nextRunTest = testCase("Test 5", "RemoveFields", "error", "POST", "/orders", 500,
                "{}", "{\"message\":\"Database down\"}");
        registry.recordOrAnnotate(nextRunTest);

        Assertions.assertThat(nextRunTest.getSameErrorAsBaselineTest()).isNull();
    }

    @Test
    void shouldNotChangeResultsCountsBaselineComparisonOrWfcFaultsWhenAnnotated() {
        CatsTestCase unannotated = testCase("Test 10", "RemoveFields", "error", "POST", "/orders", 500,
                "{\"name\":\"x\"}", "{\"message\":\"Database down\"}");
        unannotated.setResultReason("Unexpected response code: 500");

        CatsTestCase annotated = testCase("Test 10", "RemoveFields", "error", "POST", "/orders", 500,
                "{\"name\":\"x\"}", "{\"message\":\"Database down\"}");
        annotated.setResultReason("Unexpected response code: 500");

        BaselineFailureRegistry registry = new BaselineFailureRegistry();
        registry.recordOrAnnotate(testCase("Test 1", "HappyPath", "error", "POST", "/orders", 500,
                "{\"name\":\"x\"}", "{\"message\":\"Database down\"}"));
        registry.recordOrAnnotate(annotated);

        CatsTestCaseSummary summaryBefore = CatsTestCaseSummary.fromCatsTestCase(unannotated);
        CatsTestCaseSummary summaryAfter = CatsTestCaseSummary.fromCatsTestCase(annotated);

        Assertions.assertThat(annotated.getResult()).isEqualTo(unannotated.getResult());
        Assertions.assertThat(annotated.getResultReason()).isEqualTo(unannotated.getResultReason());
        Assertions.assertThat(summaryAfter.getSameErrorAsBaselineTest()).isEqualTo("Test 1");
        Assertions.assertThat(summaryAfter.getSameErrorAsBaselineFuzzer()).isEqualTo("HappyPath");

        BaselineComparisonEngine.ComparisonResult comparison = BaselineComparisonEngine.compareSummaries(
                List.of(summaryBefore), List.of(summaryAfter), 1L, 1L);
        Assertions.assertThat(comparison.hasRegressions(true)).isFalse();
        Assertions.assertThat(comparison.unchangedErrors()).isEqualTo(1);

        Assertions.assertThat(WfcFaultClassifier.classify(summaryAfter, annotated))
                .isEqualTo(WfcFaultClassifier.classify(summaryBefore, unannotated));
    }

    private static CatsTestCase testCase(String id, String fuzzer, String result, String method, String path,
                                         int statusCode, String payload, String responseBody) {
        CatsTestCase tc = new CatsTestCase();
        tc.setTestId(id);
        tc.setFuzzer(fuzzer);
        tc.setResult(result);
        tc.setContractPath(path);
        tc.setPath(path);
        tc.setRequest(CatsRequest.builder().httpMethod(method).url("http://localhost:8080" + path).payload(payload).build());
        tc.setResponse(CatsResponse.builder().responseCode(statusCode).body(responseBody).build());
        return tc;
    }
}
