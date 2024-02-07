package com.endava.cats.args;

import jakarta.inject.Singleton;
import picocli.CommandLine;

/**
 * Holds arguments related to conditions that will cause continuous fuzzing to stop.
 */
@Singleton
public class StopArguments {
    @CommandLine.Option(names = {"--stopAfterTimeInSec", "--st"},
            description = "Amount of time in seconds for how long the continuous fuzzing will run before stopping")
    private long stopAfterTimeInSec;

    @CommandLine.Option(names = {"--stopAfterErrors", "--se"},
            description = "Number of errors after which the continuous fuzzing will stop running. Errors are defined as conditions matching the given match arguments")
    private long stopAfterErrors;

    @CommandLine.Option(names = {"--stopAfterMutations", "--sm"},
            description = "Number of mutations (test cases) after which the continuous fuzzing will stop running")
    private long stopAfterMutations;

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
        return isErrorThresholdTriggered(errors) || isNumberOfTestsThresholdTriggered(tests) ||
                isTimeThresholdTriggered(startTimeInMs);
    }

    private boolean isTimeThresholdTriggered(long startTimeInMs) {
        if (stopAfterTimeInSec == 0) {
            return false;
        }

        long elapsedTimeInSeconds = (System.currentTimeMillis() - startTimeInMs) / 1000;
        return elapsedTimeInSeconds >= stopAfterTimeInSec;
    }

    private boolean isNumberOfTestsThresholdTriggered(long tests) {
        if (stopAfterMutations == 0) {
            return false;
        }
        return tests >= stopAfterMutations;
    }

    private boolean isErrorThresholdTriggered(long errors) {
        if (stopAfterErrors == 0) {
            return false;
        }
        return errors >= stopAfterErrors;
    }
}
