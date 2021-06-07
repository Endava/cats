package com.endava.cats.fuzzer;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;

import java.util.Collections;
import java.util.List;

/**
 * Implement this in order to provide concrete fuzzing implementations.
 */
public interface Fuzzer {

    /**
     * The actual business logic of the Fuzzer.
     *
     * @param data the data constructed by the {@code com.endava.cats.model.factory.FuzzingDataFactory}
     */
    void fuzz(FuzzingData data);

    /**
     * Simple description about the logic of the Fuzzer
     *
     * @return description
     */
    String description();

    /**
     * Provides a list of HTTP methods that the implementing Fuzzer will not apply to.
     *
     * @return a list of HTTP methods the Fuzzer will skip running
     */
    default List<HttpMethod> skipForHttpMethods() {
        return Collections.emptyList();
    }

    /**
     * Provides a list of payload fields that the implementing Fuzzer will not apply to.
     *
     * @return a list of payload fields
     */
    default List<String> skipForFields() {
        return Collections.emptyList();
    }
}
