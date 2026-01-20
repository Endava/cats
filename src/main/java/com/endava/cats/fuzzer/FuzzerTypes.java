package com.endava.cats.fuzzer;

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
    public static final String FUNCTIONAL = "FunctionalFuzzer";

    /**
     * SecurityFuzzer - runs security tests from YAML files.
     */
    public static final String SECURITY = "SecurityFuzzer";

    /**
     * RandomFuzzer - runs continuous random fuzzing.
     */
    public static final String RANDOM = "RandomFuzzer";

    /**
     * Linter - runs OpenAPI contract linting.
     */
    public static final String LINTER = "Linter";
}
