package com.endava.cats.report;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.stream.IntStream;

@QuarkusTest
class ExecutionStatisticsListenerTest {

    @Test
    void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfSkippedTests_thenTheSkippedTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseSkipped();// these are ignored in the total count

        Assertions.assertThat(listener.getSkipped()).isOne();
        Assertions.assertThat(listener.getErrors()).isZero();
        Assertions.assertThat(listener.getAll()).isZero();
    }

    @Test
    void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfErrorTests_thenTheErrorTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseErrors();

        Assertions.assertThat(listener.getErrors()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfSuccessTests_thenTheSuccessTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseSuccess();

        Assertions.assertThat(listener.getSuccess()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfWarnTests_thenTheWarnTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseWarns();

        Assertions.assertThat(listener.getWarns()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfAllTypesOfTestsTests_thenTheTotalNumberOfTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseWarns();
        listener.increaseSuccess();
        listener.increaseSkipped(); //these are ignored in the total count
        listener.increaseErrors();

        Assertions.assertThat(listener.getAll()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"2,5,false", "4,5,true"})
    void shouldTestForAuthErrors(int authErrors, int all, boolean expected) {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        IntStream.range(0, authErrors).forEach(element -> listener.increaseAuthErrors());
        IntStream.range(0, all).forEach(element -> listener.increaseErrors());

        Assertions.assertThat(listener.areManyAuthErrors()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2,5,false", "4,5,true"})
    void shouldTestForIOErrors(int authErrors, int all, boolean expected) {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        IntStream.range(0, authErrors).forEach(element -> listener.increaseIoErrors());
        IntStream.range(0, all).forEach(element -> listener.increaseErrors());

        Assertions.assertThat(listener.areManyIoErrors()).isEqualTo(expected);
    }
}
