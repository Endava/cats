package com.endava.cats.fuzzer.special.mutators.api;

/**
 * The Mutator interface represents an abstraction for mutating input strings.
 * Implementations of this interface provide methods to apply various types of mutations
 * to input strings, producing modified output strings.
 */
public interface Mutator {

    /**
     * Applies a mutation to the input string.
     *
     * @param inputJson     The input JSON to be mutated
     * @param selectedField The field within the JSON which is the primary target of mutation
     * @return The mutated output string
     */
    String mutate(String inputJson, String selectedField);

    /**
     * The name of the mutator.
     *
     * @return the description of the mutator
     */
    String description();
}
