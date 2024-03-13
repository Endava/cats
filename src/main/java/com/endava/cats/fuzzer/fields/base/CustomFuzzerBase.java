package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.fuzzer.api.Fuzzer;

import java.util.List;

/**
 * Marker interface for fuzzers using CATS dsl.
 */
public interface CustomFuzzerBase extends Fuzzer {

    /**
     * Retrieves a list of reserved words.
     *
     * @return A {@link List} of {@link String} representing reserved words.
     */
    List<String> requiredKeywords();
}
