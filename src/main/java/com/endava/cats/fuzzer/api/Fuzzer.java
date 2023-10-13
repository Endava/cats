package com.endava.cats.fuzzer.api;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;

import java.util.Collections;
import java.util.List;

/**
 * A Fuzzer is the main logical entity of CATS.
 * Each Fuzzer will try to be run against each field from a given request payload or query params.
 * There are certain conditions which may prevent a Fuzzer from running like data types for example.
 * A Fuzzer targeting boolean fields should not be run against a date field.
 * <br/>
 * Implement this in order to provide concrete fuzzing implementations.
 */
public interface Fuzzer {

    /**
     * The actual business logic of the Fuzzer.
     *
     * @param data the data constructed by the {@code com.endava.cats.factory.FuzzingDataFactory}
     */
    void fuzz(FuzzingData data);

    /**
     * Simple description about the logic of the Fuzzer. This is displayed in the console when using {@code --help}
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
