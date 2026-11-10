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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

@QuarkusTest
class WfcFaultClassifierTest {
    private static final Set<Reason> NON_FAULT_OUTCOME_REASONS = EnumSet.of(
            Reason.ALL_GOOD, Reason.NOT_FOUND, Reason.RESPONSE_BODY_TRUNCATED,
            Reason.UNEXPECTED_EXCEPTION, Reason.RESPONSE_MATCHES_ARGUMENTS);

    private static final Set<Reason> TRANSPORT_FAILURE_REASONS = EnumSet.of(
            Reason.EMPTY_REPLY_FROM_SERVER, Reason.CONNECTION_REFUSED, Reason.READ_TIMEOUT, Reason.WRITE_TIMEOUT,
            Reason.CONNECTION_TIMEOUT, Reason.HTTP_PROTOCOL_EXCEPTION, Reason.CONNECTION_RESET);

    private static final Set<Reason> SERVER_ERROR_REASONS = EnumSet.of(
            Reason.SERVER_ERROR, Reason.PUT_RETRY_SERVER_ERROR, Reason.PUT_RETRY_BROKE_READ,
            Reason.STALE_IF_MATCH_SERVER_ERROR, Reason.CONDITIONAL_GET_SERVER_ERROR, Reason.CROSS_PARENT_SERVER_ERROR);

    @ParameterizedTest
    @MethodSource("reasonsRequiringFaultCategory")
    void shouldMapEveryReasonToSpecificFaultCategory(Reason reason) {
        CatsTestCase testCase = buildTestCase("GET", "/pets", 200, "warning", reason.value(), "AnyOracleFuzzer", "{}");

        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(testCase), testCase))
                .as("Reason.%s must be mapped in WfcFaultClassifier to a DefinedFaultCategory or a CatsWfcFaultCategory (9xx), "
                        + "or be added to one of the exemption sets in WfcFaultClassifierTest", reason.name())
                .extracting(FaultCategoryId::getCode)
                .isNotEmpty()
                .doesNotContain(CatsWfcFaultCategory.CATS_UNEXPECTED_BEHAVIOUR.getCode());
    }

    private static Stream<Reason> reasonsRequiringFaultCategory() {
        return Arrays.stream(Reason.values())
                .filter(Predicate.not(NON_FAULT_OUTCOME_REASONS::contains))
                .filter(Predicate.not(TRANSPORT_FAILURE_REASONS::contains))
                .filter(Predicate.not(SERVER_ERROR_REASONS::contains));
    }

    @ParameterizedTest
    @MethodSource("serverErrorReasons")
    void shouldClassifyServerErrorReasonsThroughHttpStatus500(Reason reason) {
        CatsTestCase testCase = buildTestCase("PUT", "/pets/1", 500, "error", reason.value(), "AnyOracleFuzzer", "{}");

        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(testCase), testCase))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.HTTP_STATUS_500.getCode());
    }

    private static Stream<Reason> serverErrorReasons() {
        return SERVER_ERROR_REASONS.stream();
    }

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
                .contains(DefinedFaultCategory.SECURITY_COMMAND_INJECTION.getCode());

        CatsTestCase possibleCmdInj = buildTestCase("POST", "/exec", 200, "error",
                Reason.POSSIBLE_COMMAND_INJECTION.value(), "CommandInjectionInStringFields", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(possibleCmdInj), possibleCmdInj))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SECURITY_COMMAND_INJECTION.getCode());

        CatsTestCase noSqlInj = buildTestCase("POST", "/query", 200, "error",
                Reason.NOSQL_INJECTION.value(), "NoSqlInjectionInStringFields", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(noSqlInj), noSqlInj))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SECURITY_NOSQL_INJECTION.getCode());

        CatsTestCase excessiveData = buildTestCase("GET", "/users/1", 200, "error",
                Reason.WRITE_ONLY_FIELDS_EXPOSED.value(), "HappyPathFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(excessiveData), excessiveData))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SECURITY_EXCESSIVE_DATA_EXPOSURE.getCode());

        CatsTestCase sensitiveData = buildTestCase("GET", "/users/1", 200, "error",
                Reason.SENSITIVE_DATA_EXPOSED.value(), "SensitiveDataExposureFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(sensitiveData), sensitiveData))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SECURITY_EXCESSIVE_DATA_EXPOSURE.getCode());

        CatsTestCase missingSecHeaders = buildTestCase("GET", "/users", 200, "error",
                Reason.MISSING_SECURITY_HEADERS.value(), "CheckSecurityHeadersFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(missingSecHeaders), missingSecHeaders))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SECURITY_MISSING_HEADERS.getCode());

        CatsTestCase brokenRead = buildTestCase("GET", "/users", 500, "error",
                Reason.READ_BROKEN_AFTER_FUZZING.value(), "ReadResourcesAfterFuzzingFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(brokenRead), brokenRead))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.HTTP_BROKEN_READ.getCode());

        CatsTestCase updateReadInconsistency = buildTestCase("PUT", "/users/1", 200, "error",
                Reason.UPDATE_READ_INCONSISTENCY.value(), "UpdateResourcesStateConsistencyFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(updateReadInconsistency), updateReadInconsistency))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.HTTP_UPDATE_READ_INCONSISTENCY.getCode());

        CatsTestCase staleEtag = buildTestCase("PUT", "/users/1", 200, "error",
                Reason.STALE_IF_MATCH_ACCEPTED.value(), "ConditionalEtagFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(staleEtag), staleEtag))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.HTTP_STALE_ETAG_ACCEPTED.getCode());

        CatsTestCase wrongAllow = buildTestCase("TRACE", "/users", 405, "warning",
                Reason.WRONG_ALLOW_HEADER.value(), "HttpMethodsFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(wrongAllow), wrongAllow))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.SCHEMA_INVALID_ALLOW.getCode());

        CatsTestCase hiddenEndpoint = buildTestCase("DELETE", "/users", 200, "error",
                "Unexpected response code: 200", "HttpMethodsFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(hiddenEndpoint), hiddenEndpoint))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.SECURITY_HIDDEN_ACCESSIBLE_ENDPOINT.getCode());

        CatsTestCase putCrash = buildTestCase("PUT", "/users/1", 500, "error",
                Reason.PUT_RETRY_SERVER_ERROR.value(), "PutIdempotencyFuzzer", "{\"error\":\"boom\"}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(putCrash), putCrash))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.HTTP_STATUS_500.getCode());

        CatsTestCase exactBoundaryRejected = buildTestCase("POST", "/users", 422, "error",
                "Unexpected response code: 422", "MinLengthExactValuesInStringFieldsFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(exactBoundaryRejected), exactBoundaryRejected))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.SCHEMA_VALID_INPUT_REJECTED.getCode());
    }

    @Test
    void shouldExposeCatsWfcFaultCategoryMetadataAndFallbackClassification() {
        CatsWfcFaultCategory category = CatsWfcFaultCategory.CATS_UNEXPECTED_BEHAVIOUR;
        Assertions.assertThat(category.getId()).isEqualTo("CATS_UNEXPECTED_BEHAVIOUR");
        Assertions.assertThat(category.getCode()).isEqualTo(907);
        Assertions.assertThat(category.getDescriptiveName()).isEqualTo("Unexpected API Behaviour");
        Assertions.assertThat(category.getTestCaseLabel()).isEqualTo("unexpectedApiBehaviour");
        Assertions.assertThat(category.getFullDescription()).contains("oracle expectations");

        Assertions.assertThat(WfcFaultClassifier.classify(null, null)).isEmpty();

        CatsTestCase fallbackError = buildTestCase("POST", "/users", 400, "error",
                "Custom oracle failure", "CustomFuzzer", "{}");
        Set<FaultCategoryId> classified = WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(fallbackError), null);
        Assertions.assertThat(classified)
                .singleElement()
                .satisfies(fc -> {
                    Assertions.assertThat(fc.getCode()).isEqualTo(907);
                    Assertions.assertThat(fc.getId()).isEqualTo("CATS_UNEXPECTED_BEHAVIOUR");
                    Assertions.assertThat(fc.getContext()).isEqualTo("Custom oracle failure");
                });
    }

    @Test
    void shouldClassifySecondPhaseAndOracleEdgeCases() {
        CatsTestCase nonStandardCode = buildTestCase("GET", "/pets", 600, "error", "", "HappyPath", "");
        CatsTestCaseSummary nonStandardSummary = CatsTestCaseSummary.fromCatsTestCase(nonStandardCode);
        ReflectionTestUtils.setField(nonStandardSummary, "httpMethod", null);
        Assertions.assertThat(WfcFaultClassifier.classify(nonStandardSummary, nonStandardCode))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.HTTP_STATUS_NO_NON_STANDARD_CODES.getCode());

        CatsTestCase deleteStillAvailable = buildTestCase("GET", "/pets/1", 200, "error",
                "Unexpected response code: 200", "CheckDeletedResourcesNotAvailableFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(deleteStillAvailable), deleteStillAvailable))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.HTTP_NONWORKING_DELETE.getCode());

        CatsTestCase repeatedCreatePut = buildTestCase("PUT", "/pets/1", 201, "error",
                "Unexpected response code: 201", "PutIdempotencyFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(repeatedCreatePut), repeatedCreatePut))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.HTTP_REPEATED_CREATE_PUT.getCode());

        CatsTestCase repeatedCreateReason = buildTestCase("PUT", "/pets/1", 201, "error",
                Reason.REPEATED_CREATE_PUT.value(), "PutIdempotencyFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(repeatedCreateReason), repeatedCreateReason))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.HTTP_REPEATED_CREATE_PUT.getCode());

        CatsTestCase rejectedUpdate = buildTestCase("GET", "/pets/1", 200, "error",
                Reason.REJECTED_UPDATE_PARTIALLY_APPLIED.value(), "RejectedUpdateSideEffectsFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(rejectedUpdate), rejectedUpdate))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.HTTP_SIDE_EFFECTS_FAILED_MODIFICATION.getCode());

        CatsTestCase optionsAllow = buildTestCase("OPTIONS", "/pets", 200, "warning",
                Reason.OPTIONS_ALLOW_MISMATCH.value(), "OptionsAllowConformanceFuzzer", "");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(optionsAllow), optionsAllow))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.SCHEMA_INVALID_ALLOW.getCode());

        CatsTestCase filterIgnored = buildTestCase("GET", "/pets", 200, "warning",
                Reason.FILTER_PARAMETER_IGNORED.value(), "FilterParameterHonoredFuzzer", "[]");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(filterIgnored), filterIgnored))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(CatsWfcFaultCategory.CATS_FILTER_PARAMETER_IGNORED.getCode());

        CatsTestCase invalidLocation = buildTestCase("GET", "/pets/1", 400, "error",
                "Location header points to invalid resource", "CreatedResourceLocationFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(invalidLocation), invalidLocation))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.HTTP_INVALID_LOCATION.getCode());

        CatsTestCase bypassAuthModify = buildTestCase("PUT", "/pets/1", 200, "error",
                "Unexpected response code: 200", "BypassAuthenticationFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(bypassAuthModify), bypassAuthModify))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.SECURITY_ANONYMOUS_MODIFICATIONS.getCode());

        CatsTestCase bypassAuthRead = buildTestCase("GET", "/pets", 200, "error",
                "Unexpected response code: 200", "BypassAuthenticationFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(bypassAuthRead), bypassAuthRead))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.SECURITY_DECLARED_AUTH_NOT_ENFORCED.getCode());

        CatsTestCase idor = buildTestCase("GET", "/pets/2", 200, "error",
                "Unexpected behaviour", "InsecureDirectObjectReferencesFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(idor), idor))
                .extracting(FaultCategoryId::getCode)
                .contains(DefinedFaultCategory.SECURITY_INCONSISTENT_WRITE_AUTHORIZATION.getCode());

        CatsTestCase positive406 = buildTestCase("GET", "/pets", 406, "error",
                Reason.UNDOCUMENTED_RESPONSE_CODE.value(), "HappyPathFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(positive406), positive406))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(DefinedFaultCategory.SCHEMA_STATUS_HAS_406_IF_ACCEPT.getCode());

        CatsTestCase unexpectedBehaviourNegative400 = buildTestCase("POST", "/pets", 400, "error",
                Reason.UNEXPECTED_BEHAVIOUR.value(), "RemoveFieldsFuzzer", "{}");
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(unexpectedBehaviourNegative400), unexpectedBehaviourNegative400))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(CatsWfcFaultCategory.CATS_UNEXPECTED_BEHAVIOUR.getCode());

        CatsTestCase compliant401WithHeader = new CatsTestCase();
        compliant401WithHeader.setTestId("Test 401");
        compliant401WithHeader.setResult("success");
        compliant401WithHeader.setRequest(CatsRequest.builder().httpMethod("GET").payload("").build());
        compliant401WithHeader.setResponse(CatsResponse.builder().responseCode(401).body("")
                .headers(List.of(new KeyValuePair<>(null, "v"), new KeyValuePair<>("WWW-Authenticate", "Bearer"))).build());
        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(compliant401WithHeader), compliant401WithHeader))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideOracleReasonMappings")
    void shouldMapOracleReasonsToWfcAndCatsCategories(String method, String result, String reason, String fuzzer,
                                                      int expectedFaultCode) {
        CatsTestCase testCase = buildTestCase(method, "/pets/1", 200, result, reason, fuzzer, "{}");

        Assertions.assertThat(WfcFaultClassifier.classify(CatsTestCaseSummary.fromCatsTestCase(testCase), testCase))
                .extracting(FaultCategoryId::getCode)
                .containsExactly(expectedFaultCode);
    }

    private static Stream<Arguments> provideOracleReasonMappings() {
        return Stream.of(
                Arguments.of("PATCH", "warning", Reason.PATCH_CLOBBERED_OMITTED_FIELDS.value(), "PatchOmittedFieldsClobberFuzzer", DefinedFaultCategory.HTTP_INVALID_MERGE_PATCH.getCode()),
                Arguments.of("POST", "error", Reason.LDAP_INJECTION.value(), "LdapInjectionInStringFieldsFuzzer", DefinedFaultCategory.SECURITY_LDAP_INJECTION.getCode()),
                Arguments.of("POST", "error", Reason.POSSIBLE_LDAP_INJECTION.value(), "LdapInjectionInStringFieldsFuzzer", DefinedFaultCategory.SECURITY_LDAP_INJECTION.getCode()),
                Arguments.of("POST", "error", Reason.LDAP_DATA_EXTRACTION.value(), "LdapInjectionInStringFieldsFuzzer", DefinedFaultCategory.SECURITY_LDAP_INJECTION.getCode()),
                Arguments.of("POST", "error", Reason.LDAP_AUTHENTICATION_BYPASS.value(), "LdapInjectionInStringFieldsFuzzer", DefinedFaultCategory.SECURITY_LDAP_INJECTION.getCode()),
                Arguments.of("GET", "warning", Reason.PAGINATION_LIMIT_IGNORED.value(), "PaginationLimitHonoredFuzzer", CatsWfcFaultCategory.CATS_PAGINATION_LIMIT_IGNORED.getCode()),
                Arguments.of("GET", "warning", Reason.CONDITIONAL_GET_IGNORED.value(), "ConditionalEtagFuzzer", CatsWfcFaultCategory.CATS_CONDITIONAL_GET_IGNORED.getCode()),
                Arguments.of("POST", "error", Reason.ARITHMETIC_TOTAL_MISMATCH_ACCEPTED.value(), "ArithmeticTotalMismatchFieldsFuzzer", CatsWfcFaultCategory.CATS_ARITHMETIC_TOTAL_MISMATCH_ACCEPTED.getCode()),
                Arguments.of("PUT", "error", Reason.PATH_BODY_ID_MISMATCH_ACCEPTED.value(), "PathBodyIdMismatchFuzzer", CatsWfcFaultCategory.CATS_PATH_BODY_ID_MISMATCH_ACCEPTED.getCode())
        );
    }

    @Test
    void shouldKeepCatsFaultCategoryCodesUniqueWithinWfcReservedRange() {
        Assertions.assertThat(CatsWfcFaultCategory.values())
                .extracting(CatsWfcFaultCategory::getCode)
                .doesNotHaveDuplicates()
                .allSatisfy(code -> Assertions.assertThat(code).isBetween(900, 999));
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
