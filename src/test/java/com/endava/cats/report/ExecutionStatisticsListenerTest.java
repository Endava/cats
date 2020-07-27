package com.endava.cats.report;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExecutionStatisticsListenerTest {

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfSkippedTests_thenTheSkippedTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseSkipped();// these are ignored in the total count

        Assertions.assertThat(listener.getSkipped()).isOne();
        Assertions.assertThat(listener.getErrors()).isZero();
        Assertions.assertThat(listener.getAll()).isZero();
    }

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfErrorTests_thenTheErrorTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseErrors();

        Assertions.assertThat(listener.getErrors()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfSuccessTests_thenTheSuccessTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseSuccess();

        Assertions.assertThat(listener.getSuccess()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfWarnTests_thenTheWarnTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseWarns();

        Assertions.assertThat(listener.getWarns()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfAllTypesOfTestsTests_thenTheTotalNumberOfTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseWarns();
        listener.increaseSuccess();
        listener.increaseSkipped(); //these are ignored in the total count
        listener.increaseErrors();

        Assertions.assertThat(listener.getAll()).isEqualTo(3);
    }
}
