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
}
