package com.endava.cats.model.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CatsTestCaseSummaryTest {


    @Test
    void givenTwoTestCaseSummaryInstancesWithNoIntPart_whenComparingThem_thenTheyAreNotEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        testCase1.setResponse(CatsResponse.empty());
        testCase1.setRequest(CatsRequest.builder().httpMethod("POST").build());
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase("ID", testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase("ID", testCase1);

        Assertions.assertThat(summary1).isEqualByComparingTo(summary2);
    }

    @Test
    void givenTwoTestCaseSummaryInstancesWithTheDifferentStringParts_whenComparingThem_thenTheyAreNotEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        testCase1.setResponse(CatsResponse.empty());
        testCase1.setRequest(CatsRequest.builder().httpMethod("POST").build());
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase("ID1", testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase("JD1", testCase1);

        Assertions.assertThat(summary1).isNotEqualByComparingTo(summary2);
    }

    @Test
    void givenTwoTestCaseSummaryInstancesWithTheSameDetails_whenComparingThem_thenTheyAreEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        testCase1.setResponse(CatsResponse.empty());
        testCase1.setRequest(CatsRequest.builder().httpMethod("POST").build());
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase("ID1", testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase("ID1", testCase1);

        Assertions.assertThat(summary1).isEqualByComparingTo(summary2);
    }

    @Test
    void givenTwoTestCaseSummaryInstancesWithTheDifferentDetails_whenComparingThem_thenTheyAreNotEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        testCase1.setResponse(CatsResponse.empty());
        testCase1.setRequest(CatsRequest.builder().httpMethod("POST").build());
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase("ID1", testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase("ID2", testCase1);

        Assertions.assertThat(summary1).isNotEqualByComparingTo(summary2);
    }

    @Test
    void givenATestCase_whenGettingToString_thenTheScenarioDetailsAreReturned() {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setScenario("Scenario");

        Assertions.assertThat(testCase).hasToString("CatsTestCase(scenario=Scenario)");
    }
}
