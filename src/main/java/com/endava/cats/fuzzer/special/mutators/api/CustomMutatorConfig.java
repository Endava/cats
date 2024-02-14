package com.endava.cats.fuzzer.special.mutators.api;

import java.util.List;

/**
 * Stores the configuration of a custom mutator. Each filed corresponds to an entry in the custom mutator file.
 *
 * @param name   the name of the custom mutator
 * @param type   the fuzzing type, see {@code Type}
 * @param values the values that will be used for fuzzing
 */
public record CustomMutatorConfig(String name, Type type, List<Object> values) {

    public enum Type {
        TRAIL, INSERT, PREFIX, REPLACE, REPLACE_BODY, IN_BODY
    }
}
