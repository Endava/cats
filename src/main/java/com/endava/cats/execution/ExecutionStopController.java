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
    private long initialCompletedTests;

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
        initialCompletedTests = executionStatisticsListener.getCompletedTests();
    }

    /**
     * Prevents a new test from starting when a limit was reached since the previous test.
     */
    public synchronized void checkBeforeTest() {
        checkLimits();
    }

    /**
     * Stops when a limit is reached after a test completes.
     */
    public synchronized void checkAfterTest() {
        if (!active) {
            return;
        }
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
        long sessionCompletedTests = Math.max(0,
                executionStatisticsListener.getCompletedTests() - initialCompletedTests);
        stopArguments.triggeredCondition(sessionErrors, sessionCompletedTests, startedAtMillis)
                .ifPresent(this::stopExecution);
    }

    private void stopExecution(StopArguments.StopCondition condition) {
        active = false;
        throw new CatsExecutionLimitReachedException(stopArguments.describe(condition));
    }
}
