package com.endava.cats.context;

import com.endava.cats.factory.NoMediaType;
import com.endava.cats.openapi.OpenApiUtils;
import io.swagger.v3.oas.models.OpenAPI;
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
    private final Map<String, List<String>> generatedExamplesCache = new HashMap<>();
    private CatsConfiguration catsConfiguration;

    /**
     * Returns the expected HTTP response code from the --fuzzConfig file
     *
     * @param fuzzer the name of the fuzzer
     * @return the value of the property if found or null otherwise
     */
    public String getExpectedResponseCodeConfigured(String fuzzer) {
        return this.fuzzersConfiguration.getProperty(fuzzer);
    }

    public void init(OpenAPI openAPI, List<String> contentType, Properties fuzzersConfiguration, CatsConfiguration catsConfiguration) {
        Map<String, Schema> allSchemasFromOpenApi = OpenApiUtils.getSchemas(openAPI, contentType);
        this.getSchemaMap().putAll(allSchemasFromOpenApi);
        this.getSchemaMap().put(NoMediaType.EMPTY_BODY, NoMediaType.EMPTY_BODY_SCHEMA);
        this.getExampleMap().putAll(OpenApiUtils.getExamples(openAPI));
        this.getFuzzersConfiguration().putAll(fuzzersConfiguration);

        //sometimes OpenAPI generator adds a "" entry
        this.getSchemaMap().remove("");
        this.catsConfiguration = catsConfiguration;
    }

    /**
     * Checks if the example for a given key has already been generated
     *
     * @param key the key to check
     * @return true if the example has already been generated, false otherwise
     */
    public boolean isExampleAlreadyGenerated(String key) {
        return this.generatedExamplesCache.containsKey(key);
    }

    /**
     * Adds a generated example to the cache
     *
     * @param key      the key to add
     * @param examples the examples to add
     */
    public void addGeneratedExample(String key, List<String> examples) {
        this.generatedExamplesCache.put(key, examples);
    }
}
