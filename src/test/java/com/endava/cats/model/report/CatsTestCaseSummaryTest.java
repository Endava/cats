package com.endava.cats.model.report;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class CatsTestCaseSummaryTest {

    @Test
    public void givenTwoTestCaseSummaryInstancesWithTheSameDetails_whenComparingThem_thenTheyAreEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase("ID1", testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase("ID1", testCase1);

        Assertions.assertThat(summary1).isEqualByComparingTo(summary2);
    }

    @Test
    public void givenTwoTestCaseSummaryInstancesWithTheDifferentDetails_whenComparingThem_thenTheyAreNotEqual() {
        CatsTestCase testCase1 = new CatsTestCase();
        CatsTestCaseSummary summary1 = CatsTestCaseSummary.fromCatsTestCase("ID1", testCase1);
        CatsTestCaseSummary summary2 = CatsTestCaseSummary.fromCatsTestCase("ID2", testCase1);

        Assertions.assertThat(summary1).isNotEqualByComparingTo(summary2);
    }
}
