package com.endava.cats.pool;

import java.util.Set;

record StoredValue(Object value, String sourceLocation, Set<String> aliases, ValueShape shape) {
    StoredValue(Object value, String sourceLocation, Set<String> aliases) {
        this(value, sourceLocation, aliases, ResourceSemanticNaming.valueShape(value));
    }

    StoredValue withAliases(Set<String> newAliases) {
        return new StoredValue(value, sourceLocation, Set.copyOf(newAliases), shape);
    }
}
