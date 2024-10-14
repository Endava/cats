package com.endava.cats.fuzzer.special.mutators.api;

/**
 * Marker interface for mutators that mutate the request headers.
 */
public interface HeadersMutator extends Mutator {

    @Override
    default String mutate(String inputJson, String selectedField) {
        return inputJson;
    }

}
