package com.endava.cats.fuzzer.special.mutators.api;

import com.endava.cats.model.CatsHeader;

import java.util.Collection;

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
     * Applies a mutation to one of the headers.
     *
     * @param headers the request headers
     * @return a list of headers with at least one mutated
     */
    Collection<CatsHeader> mutate(Collection<CatsHeader> headers);

    /**
     * The name of the mutator.
     *
     * @return the description of the mutator
     */
    String description();
}
