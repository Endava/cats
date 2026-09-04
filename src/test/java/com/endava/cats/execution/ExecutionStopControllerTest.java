package com.endava.cats.execution;

import com.endava.cats.args.StopArguments;
import com.endava.cats.exception.CatsExecutionLimitReachedException;
import com.endava.cats.report.ExecutionStatisticsListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void shouldStopAfterTheConfiguredNumberOfCompletedTests() {
        ReflectionTestUtils.setField(stopArguments, "stopAfterMutations", 2);
        controller.startSession();

        controller.checkAfterTest();

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
