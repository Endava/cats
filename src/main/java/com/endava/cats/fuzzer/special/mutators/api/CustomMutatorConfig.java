package com.endava.cats.fuzzer.special.mutators.api;

import java.util.List;

public record CustomMutatorConfig(String name, Type type, List<Object> values) {

    public enum Type {
        TRAIL, INSERT, PREFIX, REPLACE, REPLACE_BODY, IN_BODY
    }
}
