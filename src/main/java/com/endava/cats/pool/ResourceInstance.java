package com.endava.cats.pool;

import com.endava.cats.http.HttpMethod;

import java.util.Map;

record ResourceInstance(long sequence, String sourcePath, HttpMethod sourceMethod,
                        Map<String, StoredValue> values, boolean fuzzed, boolean active) {
    ResourceInstance deleted() {
        return new ResourceInstance(sequence, sourcePath, sourceMethod, values, fuzzed, false);
    }
}
