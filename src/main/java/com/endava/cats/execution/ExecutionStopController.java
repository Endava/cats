package com.endava.cats.execution;

import com.endava.cats.args.StopArguments;
import com.endava.cats.exception.CatsExecutionLimitReachedException;
import com.endava.cats.report.ExecutionStatisticsListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Enforces global execution limits at test-case boundaries.
 */
@ApplicationScoped
public class ExecutionStopController {
    private final StopArguments stopArguments;
    private final ExecutionStatisticsListener executionStatisticsListener;

    private boolean active;
    private long startedAtMillis;
    private long initialErrors;
    private long completedTests;

    /**
     * Creates the controller.
     *
     * @param stopArguments               configured execution limits
     * @param executionStatisticsListener current execution statistics
     */
    @Inject
    public ExecutionStopController(StopArguments stopArguments,
                                   ExecutionStatisticsListener executionStatisticsListener) {
        this.stopArguments = stopArguments;
        this.executionStatisticsListener = executionStatisticsListener;
    }

    /**
     * Starts tracking a new session. Statistics accumulated by previous sessions are excluded.
     */
    public synchronized void startSession() {
        active = stopArguments.isAnyStopConditionProvided();
        startedAtMillis = System.currentTimeMillis();
        initialErrors = executionStatisticsListener.getErrors();
        completedTests = 0;
    }

    /**
     * Prevents a new test from starting when a limit was reached since the previous test.
     */
    public synchronized void checkBeforeTest() {
        checkLimits();
    }

    /**
     * Counts a fully reported test and stops when it reaches a configured limit.
     */
    public synchronized void checkAfterTest() {
        if (!active) {
            return;
        }
        completedTests++;
        checkLimits();
    }

    /**
     * Disables limit checks until another session starts.
     */
    public synchronized void finishSession() {
        active = false;
    }

    private void checkLimits() {
        if (!active) {
            return;
        }

        long sessionErrors = Math.max(0, executionStatisticsListener.getErrors() - initialErrors);
        stopArguments.triggeredCondition(sessionErrors, completedTests, startedAtMillis)
                .ifPresent(this::stopExecution);
    }

    private void stopExecution(StopArguments.StopCondition condition) {
        active = false;
        throw new CatsExecutionLimitReachedException(switch (condition) {
            case ERRORS -> limitMessage("--stopAfterErrors", stopArguments.getStopAfterErrors(), "error");
            case TESTS -> limitMessage("--stopAfterTests", stopArguments.getStopAfterMutations(), "test");
            case TIME -> limitMessage("--stopAfterTimeInSec", stopArguments.getStopAfterTimeInSec(), "second");
        });
    }

    private static String limitMessage(String option, long limit, String unit) {
        return "Execution stopped after reaching " + option + " (" + limit + " " + unit +
                (limit == 1 ? "" : "s") + ")";
    }
}
