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
        listener.increaseErrors("test");

        Assertions.assertThat(listener.getErrors()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfSuccessTests_thenTheSuccessTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseSuccess("test");

        Assertions.assertThat(listener.getSuccess()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfWarnTests_thenTheWarnTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseWarns("test");

        Assertions.assertThat(listener.getWarns()).isOne();
        Assertions.assertThat(listener.getSkipped()).isZero();
        Assertions.assertThat(listener.getAll()).isOne();
    }

    @Test
    void givenAnExecutionStatisticsListener_whenIncreasingTheNumberOfAllTypesOfTestsTests_thenTheTotalNumberOfTestsAreReportedCorrectly() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseWarns("test");
        listener.increaseSuccess("test");
        listener.increaseSkipped(); //these are ignored in the total count
        listener.increaseErrors("test");

        Assertions.assertThat(listener.getAll()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"2,5,true", "4,5,true", "1,5,false", "0,5,false", "1,1,true"})
    void shouldTestForAuthErrors(int authErrors, int all, boolean expected) {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        IntStream.range(0, authErrors).forEach(element -> listener.increaseAuthErrors());
        IntStream.range(0, all).forEach(element -> listener.increaseErrors("test"));

        Assertions.assertThat(listener.areManyAuthErrors()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2,5,true", "4,5,true", "1,5,false", "0,5,false", "2,2,true"})
    void shouldTestForIOErrors(int authErrors, int all, boolean expected) {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        IntStream.range(0, authErrors).forEach(element -> listener.increaseIoErrors());
        IntStream.range(0, all).forEach(element -> listener.increaseErrors("test"));

        Assertions.assertThat(listener.areManyIoErrors()).isEqualTo(expected);
    }

    @Test
    void shouldRecordResponseCodeDistribution() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.recordResponseCode(200);
        listener.recordResponseCode(200);
        listener.recordResponseCode(400);
        listener.recordResponseCode(500);
        listener.recordResponseCode(200);

        var distribution = listener.getResponseCodeDistribution();

        Assertions.assertThat(distribution)
                .hasSize(3)
                .containsEntry(200, 3)
                .containsEntry(400, 1)
                .containsEntry(500, 1);
    }

    @Test
    void shouldReturnEmptyDistributionWhenNoResponseCodesRecorded() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();

        var distribution = listener.getResponseCodeDistribution();

        Assertions.assertThat(distribution).isEmpty();
    }

    @Test
    void shouldReturnCopyOfDistributionMap() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.recordResponseCode(200);

        var distribution = listener.getResponseCodeDistribution();
        distribution.put(999, 100);

        Assertions.assertThat(listener.getResponseCodeDistribution()).doesNotContainKey(999);
    }

    @Test
    void shouldReturnTopFailingPathsSortedByErrorCount() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        listener.increaseErrors("/path1");
        listener.increaseErrors("/path1");
        listener.increaseErrors("/path1");
        listener.increaseErrors("/path2");
        listener.increaseErrors("/path2");
        listener.increaseErrors("/path3");

        var topPaths = listener.getTopFailingPaths(10);

        Assertions.assertThat(topPaths).hasSize(3);
        var entries = topPaths.entrySet().iterator();
        var first = entries.next();
        var second = entries.next();
        var third = entries.next();

        Assertions.assertThat(first.getKey()).isEqualTo("/path1");
        Assertions.assertThat(first.getValue()).isEqualTo(3);
        Assertions.assertThat(second.getKey()).isEqualTo("/path2");
        Assertions.assertThat(second.getValue()).isEqualTo(2);
        Assertions.assertThat(third.getKey()).isEqualTo("/path3");
        Assertions.assertThat(third.getValue()).isEqualTo(1);
    }

    @Test
    void shouldLimitTopFailingPaths() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();
        for (int i = 0; i < 15; i++) {
            listener.increaseErrors("/path" + i);
        }

        var topPaths = listener.getTopFailingPaths(10);

        Assertions.assertThat(topPaths).hasSize(10);
    }

    @Test
    void shouldReturnEmptyMapWhenNoErrors() {
        ExecutionStatisticsListener listener = new ExecutionStatisticsListener();

        var topPaths = listener.getTopFailingPaths(10);

        Assertions.assertThat(topPaths).isEmpty();
    }
}
