package com.endava.cats.pool;

import java.util.Set;

record StoredValue(Object value, String sourceLocation, Set<String> aliases) {
    StoredValue withAliases(Set<String> newAliases) {
        return new StoredValue(value, sourceLocation, Set.copyOf(newAliases));
    }
}
