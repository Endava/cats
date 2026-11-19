package com.endava.cats.fuzzer.special.mutators.api;

import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.media.Schema;

import java.util.Collection;
import java.util.Map;

/**
 * The Mutator interface represents an abstraction for mutating input strings.
 * Implementations of this interface provide methods to apply various types of mutations
 * to input strings, producing modified output strings.
 */
public interface Mutator extends Comparable<Mutator> {

    /**
     * Applies a mutation to the input string.
     *
     * @param inputJson     The input JSON to be mutated
     * @param selectedField The field within the JSON which is the primary target of mutation
     * @return The mutated output string
     */
    String mutate(String inputJson, String selectedField);

    /**
     * Applies a schema-aware mutation to the input JSON when operation {@link FuzzingData} is available.
     * Defaults to {@link #mutate(String, String)} for mutators that do not require schema metadata.
     *
     * @param inputJson     the input JSON to be mutated
     * @param selectedField the field within the JSON which is the primary target of mutation
     * @param fuzzingData   the operation fuzzing context (may be {@code null})
     * @return the mutated output string
     */
    default String mutate(String inputJson, String selectedField, FuzzingData fuzzingData) {
        return mutate(inputJson, selectedField);
    }

    /**
     * Resolves the OpenAPI {@link Schema} for {@code selectedField} when {@code fuzzingData} is present.
     *
     * @param fuzzingData   the operation fuzzing context
     * @param selectedField the field path
     * @return the field schema, or {@code null} when unavailable
     */
    static Schema<?> resolveFieldSchema(FuzzingData fuzzingData, String selectedField) {
        if (fuzzingData == null || selectedField == null) {
            return null;
        }
        Map<String, Schema> types = fuzzingData.getRequestPropertyTypes();
        return types == null ? null : types.get(selectedField);
    }


    /**
     * Indicates whether this mutator transforms, wraps, or duplicates the current value of
     * {@code selectedField} in-place and can therefore be chained after another value mutator.
     *
     * @return {@code true} when this mutator composes with a prior mutation on the same field
     */
    default boolean isComposable() {
        return false;
    }

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

    @Override
    default int compareTo(Mutator o) {
        return this.getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
    }
}
