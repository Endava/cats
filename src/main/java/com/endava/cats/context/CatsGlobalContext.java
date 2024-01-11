package com.endava.cats.context;

import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Holds global variables which should not be recomputed for each path.
 */
@Singleton
@Getter
public class CatsGlobalContext {
    private final Map<String, Schema> schemaMap = new HashMap<>();
    private final Map<String, Example> exampleMap = new HashMap<>();
    private final Map<String, Schema> requestDataTypes = new HashMap<>();
    private final List<String> additionalProperties = new ArrayList<>();
    private final List<Discriminator> discriminators = new ArrayList<>();
    private final Map<String, Deque<String>> postSuccessfulResponses = new HashMap<>();
    private final Set<String> successfulDeletes = new HashSet<>();
    private final Properties fuzzersConfiguration = new Properties();

    /**
     * Returns the expected HTTP response code from the --fuzzConfig file
     *
     * @param fuzzer the name of the fuzzer
     * @return the value of the property if found or null otherwise
     */
    public String getExpectedResponseCodeConfigured(String fuzzer) {
        return this.fuzzersConfiguration.getProperty(fuzzer);
    }
}
