package com.endava.cats.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks fuzzers which will run after first phase fuzzers. Second phase
 * fuzzers need input produced by the first phase fuzzers, this being the
 * reason for running after.
 * Unless explicitly specified otherwise, a Fuzzer is considered to run in
 * the first phase by default.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SecondPhaseFuzzer {

    /**
     * Lifecycle points at which a second phase fuzzer runs for a path.
     */
    enum Trigger {
        /**
         * After all first phase fuzzers finished for a path.
         */
        PATH_COMPLETED,
        /**
         * After the happy path DELETE requests of a path ran. These are postponed until all paths are fuzzed,
         * so that parent resources remain available while their child paths are fuzzed.
         */
        PATH_RESOURCES_DELETED
    }

    /**
     * The lifecycle points at which the fuzzer runs.
     *
     * @return the triggers for running the fuzzer
     */
    Trigger[] triggers() default {Trigger.PATH_COMPLETED};
}
