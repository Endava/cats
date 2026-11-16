package com.endava.cats.model;

import com.google.gson.Gson;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class CatsTestCaseSummaryTest {

    @Test
    void shouldPersistTypedExpectationsWithoutChangingTheDescription() {
        CatsTestCase test = new CatsTestCase();
        test.setTestId("Test 1");
        test.setExpectedResult("Should return a successful response");
        test.recordExpectedResponse(new ExpectedResponse(List.of("405")));
        test.setRequest(CatsRequest.builder().httpMethod("PUT").build());
        CatsTestCaseSummary summary = CatsTestCaseSummary.fromCatsTestCase(test);

        CatsTestCase readTest = JsonUtils.GSON.fromJson(JsonUtils.GSON.toJson(test), CatsTestCase.class);
        CatsTestCaseSummary readSummary = JsonUtils.GSON.fromJson(JsonUtils.GSON.toJson(summary), CatsTestCaseSummary.class);

        Assertions.assertThat(readTest.getExpectedResult()).isEqualTo(test.getExpectedResult());
        Assertions.assertThat(readTest.getExpectedResponseCodes()).containsExactly("405");
        Assertions.assertThat(readSummary.getExpectedResponseCodes()).containsExactly("405");
        Assertions.assertThat(readSummary.getExpectedOutcome()).isEqualTo(ExpectedResponse.Outcome.REJECTION);
        Assertions.assertThat(readSummary.hasRecordedExpectedResponse()).isTrue();
    }

    @Test
    void shouldKeepLegacyExpectationsUnknownUntilTheyAreExplicitlyRecorded() {
        CatsTestCase test = JsonUtils.GSON.fromJson("{\"expectedResult\":\"Should return [2XX]\"}", CatsTestCase.class);
        CatsTestCaseSummary summary = JsonUtils.GSON.fromJson("{}", CatsTestCaseSummary.class);

        Assertions.assertThat(test.hasRecordedExpectedResponse()).isFalse();
        Assertions.assertThat(test.getExpectedResponseCodes()).isEmpty();
        Assertions.assertThat(test.getExpectedOutcome()).isEqualTo(ExpectedResponse.Outcome.UNKNOWN);
        Assertions.assertThat(summary.hasRecordedExpectedResponse()).isFalse();
        Assertions.assertThat(summary.getExpectedOutcome()).isEqualTo(ExpectedResponse.Outcome.UNKNOWN);
    }

    @Test
    void shouldKeepWfcCompatibilityStateOutOfSummaryJson() {
        CatsTestCase test = new CatsTestCase();
        test.setTestId("Test 1");
        test.setResult("error");
        test.setRequest(CatsRequest.builder().httpMethod("POST").payload("\"TOKEN_ALPHA\"").build());
        test.setResponse(CatsResponse.builder().responseCode(500).body("{}").build());
        CatsTestCaseSummary summary = CatsTestCaseSummary.fromCatsTestCase(test);
        summary.attachWfcErrorSignature(new ErrorSignature("Error", List.of("TOKEN_ALPHA"), ""));

        Assertions.assertThat(summary.getRequestVocabulary().values()).containsExactly("TOKEN_ALPHA");
        Assertions.assertThat(summary.getWfcRequestVocabulary()).isEqualTo(RequestVocabulary.EMPTY);
        Assertions.assertThat(new Gson().toJson(summary)).doesNotContain("wfcRequestVocabulary", "wfcErrorSignature", "TOKEN_ALPHA");
    }

    @Test
    void givenTwoTestCaseSummaryInstancesWithTheDifferentStringParts_whenComparingThem_thenTheyAreNotEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        CatsTestCase testCase2 = new CatsTestCase();
        testCase1.setTestId("ID1");
        testCase2.setTestId("JD1");

        testCase1.setResponse(CatsResponse.empty());
        testCase1.setRequest(CatsRequest.builder().httpMethod("POST").build());
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase(testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase(testCase2);

        Assertions.assertThat(summary1).isNotEqualByComparingTo(summary2);
    }

    @Test
    void givenTwoTestCaseSummaryInstancesWithTheSameDetails_whenComparingThem_thenTheyAreEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        testCase1.setTestId("ID1");
        testCase1.setResponse(CatsResponse.empty());
        testCase1.setRequest(CatsRequest.builder().httpMethod("POST").build());
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase(testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase(testCase1);

        Assertions.assertThat(summary1).isEqualByComparingTo(summary2);
    }

    @Test
    void givenTwoTestCaseSummaryInstancesWithTheDifferentDetails_whenComparingThem_thenTheyAreNotEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        CatsTestCase testCase2 = new CatsTestCase();
        testCase2.setTestId("ID2");
        testCase1.setTestId("ID1");

        testCase1.setResponse(CatsResponse.empty());
        testCase1.setRequest(CatsRequest.builder().httpMethod("POST").build());

        testCase2.setResponse(CatsResponse.empty());
        testCase2.setRequest(CatsRequest.builder().httpMethod("POST").build());
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase(testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase(testCase2);

        Assertions.assertThat(summary1).isNotEqualByComparingTo(summary2);
    }

    @Test
    void givenATestCase_whenGettingToString_thenTheScenarioDetailsAreReturned() {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setScenario("Scenario");

        Assertions.assertThat(testCase).hasToString("CatsTestCase(scenario=Scenario)");
    }
}
