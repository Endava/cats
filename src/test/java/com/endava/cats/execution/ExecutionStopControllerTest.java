package com.endava.cats.execution;

import com.endava.cats.args.StopArguments;
import com.endava.cats.exception.CatsExecutionLimitReachedException;
import com.endava.cats.report.ExecutionStatisticsListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class ExecutionStopControllerTest {
    private StopArguments stopArguments;
    private ExecutionStatisticsListener executionStatisticsListener;
    private ExecutionStopController controller;

    @BeforeEach
    void setUp() {
        stopArguments = new StopArguments();
        executionStatisticsListener = new ExecutionStatisticsListener();
        controller = new ExecutionStopController(stopArguments, executionStatisticsListener);
    }

    @ParameterizedTest
    @CsvSource({"0, 14000, false", "1000, 14000, false", "1001, 14000, true", "0, 15001, true"})
    void shouldOnlyRejectWaitsThatGoPastTheActiveDeadline(long waitMillis, long nowMillis, boolean expected) {
        ReflectionTestUtils.setField(stopArguments, "stopAfterTimeInSec", 5);
        controller.startSession();
        ReflectionTestUtils.setField(controller, "startedAtMillis", 10_000L);

        Assertions.assertThat(controller.wouldExceedTimeLimit(waitMillis, nowMillis)).isEqualTo(expected);
    }

    @Test
    void shouldAllowWaitsBeforeSessionStartAndAfterSessionFinish() {
        ReflectionTestUtils.setField(stopArguments, "stopAfterTimeInSec", 5);
        Assertions.assertThat(controller.wouldExceedTimeLimit(Long.MAX_VALUE, 0)).isFalse();
        controller.startSession();
        controller.finishSession();

        Assertions.assertThat(controller.wouldExceedTimeLimit(Long.MAX_VALUE, 0)).isFalse();
        Assertions.assertThatCode(controller::checkBeforeTest).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowWaitsWhenOnlyANonTimeLimitIsConfigured() {
        ReflectionTestUtils.setField(stopArguments, "stopAfterErrors", 1);
        controller.startSession();

        Assertions.assertThat(controller.wouldExceedTimeLimit(60_000, System.currentTimeMillis())).isFalse();
    }

    @Test
    void shouldStopAfterTheConfiguredNumberOfCompletedTests() {
        ReflectionTestUtils.setField(stopArguments, "stopAfterMutations", 2);
        controller.startSession();

        executionStatisticsListener.increaseProcessedTests();
        controller.checkAfterTest();

        executionStatisticsListener.increaseProcessedTests();
        Assertions.assertThatThrownBy(controller::checkAfterTest)
                .isInstanceOf(CatsExecutionLimitReachedException.class)
                .hasMessage("Execution stopped after reaching --stopAfterTests (2 tests)");
    }

    @Test
    void shouldOnlyCountErrorsFromTheCurrentSession() {
        executionStatisticsListener.increaseErrors("/previous-run");
        ReflectionTestUtils.setField(stopArguments, "stopAfterErrors", 1);
        controller.startSession();

        controller.checkBeforeTest();
        executionStatisticsListener.increaseErrors("/current-run");

        Assertions.assertThatThrownBy(controller::checkAfterTest)
                .isInstanceOf(CatsExecutionLimitReachedException.class)
                .hasMessage("Execution stopped after reaching --stopAfterErrors (1 error)");
    }

    @Test
    void shouldStopBeforeStartingAnotherTestWhenTimeLimitElapsed() {
        ReflectionTestUtils.setField(stopArguments, "stopAfterTimeInSec", 5);
        controller.startSession();
        ReflectionTestUtils.setField(controller, "startedAtMillis", System.currentTimeMillis() - 5_000);

        Assertions.assertThatThrownBy(controller::checkBeforeTest)
                .isInstanceOf(CatsExecutionLimitReachedException.class)
                .hasMessage("Execution stopped after reaching --stopAfterTimeInSec (5 seconds)");
    }

    @Test
    void shouldDisableChecksAfterTheSessionFinishes() {
        ReflectionTestUtils.setField(stopArguments, "stopAfterMutations", 1);
        controller.startSession();
        controller.finishSession();

        Assertions.assertThatCode(controller::checkAfterTest).doesNotThrowAnyException();
    }
}
