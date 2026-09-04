package com.endava.cats.args;

import jakarta.inject.Singleton;
import lombok.Getter;
import picocli.CommandLine;

import java.util.Optional;

/**
 * Holds arguments related to conditions that will cause fuzzing to stop.
 */
@Singleton
@Getter
public class StopArguments {
    @CommandLine.Option(names = {"--stopAfterTimeInSec", "--st"},
            description = "Amount of time in seconds for how long fuzzing will run before stopping")
    private long stopAfterTimeInSec;

    @CommandLine.Option(names = {"--stopAfterErrors", "--se"},
            description = "Number of errors after which fuzzing will stop. Errors are test results reported as errors")
    private long stopAfterErrors;

    @CommandLine.Option(names = {"--stopAfterTests", "--stopAfterMutations", "--sm"},
            description = "Number of test cases after which fuzzing will stop")
    private long stopAfterMutations;

    /**
     * The configured condition that caused execution to stop.
     */
    public enum StopCondition {
        ERRORS,
        TESTS,
        TIME
    }

    /**
     * Checks if any stopXXX argument was supplied and has a positive value.
     *
     * @return true if a valid stopXXX argument was supplied, false otherwise
     */
    public boolean isAnyStopConditionProvided() {
        return stopAfterMutations > 0 || stopAfterErrors > 0 || stopAfterTimeInSec > 0;
    }


    /**
     * Checks if any of the stop condition is met.
     *
     * @param errors        the current number of errors
     * @param tests         the number of executed tests
     * @param startTimeInMs star time of fuzzing session
     * @return true if any of the stop criteria is met, false otherwise
     */
    public boolean shouldStop(long errors, long tests, long startTimeInMs) {
        return triggeredCondition(errors, tests, startTimeInMs).isPresent();
    }

    /**
     * Returns the first configured stop condition that has been reached.
     *
     * @param errors        the current number of errors
     * @param tests         the number of executed tests
     * @param startTimeInMs start time of the fuzzing session
     * @return the reached condition, or empty when execution should continue
     */
    public Optional<StopCondition> triggeredCondition(long errors, long tests, long startTimeInMs) {
        if (isErrorThresholdTriggered(errors)) {
            return Optional.of(StopCondition.ERRORS);
        }
        if (isNumberOfTestsThresholdTriggered(tests)) {
            return Optional.of(StopCondition.TESTS);
        }
        if (isTimeThresholdTriggered(startTimeInMs)) {
            return Optional.of(StopCondition.TIME);
        }
        return Optional.empty();
    }

    private boolean isTimeThresholdTriggered(long startTimeInMs) {
        if (stopAfterTimeInSec <= 0) {
            return false;
        }

        long elapsedTimeInSeconds = (System.currentTimeMillis() - startTimeInMs) / 1000;
        return elapsedTimeInSeconds >= stopAfterTimeInSec;
    }

    private boolean isNumberOfTestsThresholdTriggered(long tests) {
        if (stopAfterMutations <= 0) {
            return false;
        }
        return tests >= stopAfterMutations;
    }

    private boolean isErrorThresholdTriggered(long errors) {
        if (stopAfterErrors <= 0) {
            return false;
        }
        return errors >= stopAfterErrors;
    }
}
