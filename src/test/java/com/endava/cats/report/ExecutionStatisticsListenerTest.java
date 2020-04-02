package com.endava.cats.report;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExecutionStatisticsListenerTest {

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfSkippedTests_thenTheSkippedTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseSkipped();// these are ignored in the total count

        Assertions.assertThat(listener.getSkipped()).isEqualTo(1);
        Assertions.assertThat(listener.getErrors()).isEqualTo(0);
        Assertions.assertThat(listener.getAll()).isEqualTo(0);
    }

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfErrorTests_thenTheErrorTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseErrors();

        Assertions.assertThat(listener.getErrors()).isEqualTo(1);
        Assertions.assertThat(listener.getSkipped()).isEqualTo(0);
        Assertions.assertThat(listener.getAll()).isEqualTo(1);
    }

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfSuccessTests_thenTheSuccessTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseSuccess();

        Assertions.assertThat(listener.getSuccess()).isEqualTo(1);
        Assertions.assertThat(listener.getSkipped()).isEqualTo(0);
        Assertions.assertThat(listener.getAll()).isEqualTo(1);
    }

    @Test
    public void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfWarnTests_thenTheWarnTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseWarns();

        Assertions.assertThat(listener.getWarns()).isEqualTo(1);
        Assertions.assertThat(listener.getSkipped()).isEqualTo(0);
        Assertions.assertThat(listener.getAll()).isEqualTo(1);
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
