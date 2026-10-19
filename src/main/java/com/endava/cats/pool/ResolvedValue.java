package com.endava.cats.pool;

record ResolvedValue(ResourceInstance resource, StoredValue storedValue, int score) {
    Object value() {
        return storedValue.value();
    }
}
