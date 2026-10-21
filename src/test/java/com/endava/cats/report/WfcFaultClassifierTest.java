package com.endava.cats.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.util.KeyValuePair;
import com.webfuzzing.commons.faults.DefinedFaultCategory;
import com.webfuzzing.commons.report.FaultCategoryId;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@QuarkusTest
class WfcFaultClassifierTest {

    @Test
    void shouldClassify500And5xxAnd501StatusFaults() {
        CatsTestCase tc500 = buildTestCase("POST", "/pets", 500, "error",
                "Unexpected response code: 500", "HappyPath", "{\"title\":\"NullPointerException\"}");
        Set<FaultCategoryId> faults500 = WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(tc500), tc500);
        Assertions.assertThat(faults500).extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.HTTP_STATUS_500.getCode());

        CatsTestCase tc501 = buildTestCase("POST", "/pets", 501, "error",
                Reason.NOT_IMPLEMENTED.value(), "HappyPath", "{}");
        Set<FaultCategoryId> faults501 = WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(tc501), tc501);
        Assertions.assertThat(faults501).extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED.getCode());

        CatsTestCase tc503 = buildTestCase("GET", "/pets", 503, "error",
                "Unexpected response code: 503", "HappyPath", "{}");
        Set<FaultCategoryId> faults503 = WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(tc503), tc503);
        Assertions.assertThat(faults503).extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.HTTP_OTHER_STATUS_5XX.getCode());
    }

    @ParameterizedTest
    @MethodSource("providePassiveHttpProtocolViolations")
    void shouldClassifyPassiveHttpProtocolViolations(String method, int statusCode, String reqPayload,
                                                     String respBody, List<KeyValuePair<String, String>> respHeaders,
                                                     int expectedFaultCode) {
        CatsTestCase tc = new CatsTestCase();
        tc.setTestId("Test 1");
        tc.setResult("success");
        tc.setResultReason(Reason.ALL_GOOD.value());
        tc.setFuzzer("HappyPath");
        tc.setContractPath("/items");
        tc.setRequest(CatsRequest.builder().httpMethod(method).payload(reqPayload).build());
        tc.setResponse(CatsResponse.builder().responseCode(statusCode).body(respBody).headers(respHeaders).build());

        Set<FaultCategoryId> faults = WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(tc), tc);

        Assertions.assertThat(faults).extracting(FaultCategoryId::getCode).contains(expectedFaultCode);
    }

    private static Stream<Arguments> providePassiveHttpProtocolViolations() {
        List<KeyValuePair<String, String>> nonAuthHeaders = List.of(new KeyValuePair<>("Content-Type", "application/json"));
        return Stream.of(
                Arguments.of("DELETE", 201, "{}", "{}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_201_IF_DELETE.getCode()),
                Arguments.of("GET", 201, "{}", "{}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_201_IF_GET.getCode()),
                Arguments.of("PATCH", 201, "{}", "{}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_201_IF_PATCH.getCode()),
                Arguments.of("DELETE", 204, "{}", "{\"unexpected\":\"body\"}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_204_IF_CONTENT.getCode()),
                Arguments.of("PUT", 205, "{}", "{\"unexpected\":\"body\"}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_205_IF_CONTENT.getCode()),
                Arguments.of("POST", 304, "{}", "", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_304_IF_NO_GET_OR_HEAD.getCode()),
                Arguments.of("GET", 413, "{}", "{}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_413_IF_NO_PAYLOAD.getCode()),
                Arguments.of("GET", 415, "{}", "{}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_415_IF_NO_PAYLOAD.getCode()),
                Arguments.of("GET", 401, "{}", "{}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_401_IF_NO_WWW_AUTHENTICATE.getCode()),
                Arguments.of("POST", 405, "{}", "{}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_405_IF_NO_ALLOW.getCode()),
                Arguments.of("GET", 426, "{}", "{}", nonAuthHeaders, DefinedFaultCategory.HTTP_STATUS_NO_426_IF_NO_UPGRADE.getCode())
        );
    }

    @Test
    void shouldClassifySchemaAndSecurityAndCustomCatsFaults() {
        CatsTestCase sqli = buildTestCase("POST", "/users", 200, "error",
                Reason.SQL_INJECTION.value(), "SqlInjectionInStringFields", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(sqli), sqli))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SECURITY_SQL_INJECTION.getCode());

        CatsTestCase ssrf = buildTestCase("POST", "/webhooks", 200, "error",
                Reason.CLOUD_METADATA_REFLECTED_SSRF.value(), "SSRFInUrlFields", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(ssrf), ssrf))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SECURITY_SSRF.getCode());

        CatsTestCase validRejected = buildTestCase("POST", "/users", 400, "error",
                "Unexpected response code: 400", "HappyPathFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(validRejected), validRejected))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SCHEMA_VALID_INPUT_REJECTED.getCode());

        CatsTestCase invalidAccepted = buildTestCase("POST", "/users", 200, "error",
                "Unexpected response code: 200", "RemoveFieldsFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(invalidAccepted), invalidAccepted))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SCHEMA_VALIDATION_BYPASS.getCode());

        CatsTestCase cmdInj = buildTestCase("POST", "/exec", 200, "error",
                Reason.COMMAND_INJECTION.value(), "CommandInjectionInStringFields", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(cmdInj), cmdInj))
                .extracting(FaultCategoryId::getCode)
                .contains(CatsWfcFaultCategory.CATS_COMMAND_INJECTION.getCode());
    }

    private static CatsTestCase buildTestCase(String method, String path, int code, String result,
                                              String reason, String fuzzer, String respBody) {
        CatsTestCase tc = new CatsTestCase();
        tc.setTestId("Test 1");
        tc.setResult(result);
        tc.setResultReason(reason);
        tc.setFuzzer(fuzzer);
        tc.setContractPath(path);
        tc.setRequest(CatsRequest.builder().httpMethod(method).payload("{\"a\":1}").build());
        tc.setResponse(CatsResponse.builder().responseCode(code).body(respBody).build());
        return tc;
    }
}
