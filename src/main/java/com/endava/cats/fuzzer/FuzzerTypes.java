package com.endava.cats.fuzzer;

import com.endava.cats.fuzzer.special.FunctionalFlowFuzzer;
import com.endava.cats.fuzzer.special.FunctionalFuzzer;
import com.endava.cats.fuzzer.special.RandomFuzzer;
import com.endava.cats.fuzzer.special.SecurityFuzzer;

/**
 * Constants for special fuzzer type names used in command execution.
 * Use these constants instead of magic strings to prevent typos and enable refactoring.
 */
public final class FuzzerTypes {

    private FuzzerTypes() {
        // Constants class
    }

    /**
     * FunctionalFuzzer - runs functional tests from YAML files.
     */
    public static final String FUNCTIONAL = FunctionalFuzzer.class.getSimpleName();

    /**
     * FunctionalFlowFuzzer - runs sequential functional flow tests from YAML files.
     */
    public static final String FUNCTIONAL_FLOW = FunctionalFlowFuzzer.class.getSimpleName();

    /**
     * SecurityFuzzer - runs security tests from YAML files.
     */
    public static final String SECURITY = SecurityFuzzer.class.getSimpleName();

    /**
     * RandomFuzzer - runs continuous random fuzzing.
     */
    public static final String RANDOM = RandomFuzzer.class.getSimpleName();

    /**
     * Linter - runs OpenAPI contract linting.
     */
    public static final String LINTER = "Linter";
}
