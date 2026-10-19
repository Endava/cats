package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * A subclass of {@link DataFormat} that represents a valid data format.
 */
@Singleton
public class ValidDataFormat extends DataFormat<ValidDataFormatGenerator> {
    private static final int MAX_CACHED_PATTERNS = 512;
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /**
     * Injects an {@link Instance} of {@link ValidDataFormatGenerator} into the constructor.
     *
     * @param generators An {@link Instance} of {@link ValidDataFormatGenerator}
     */
    @Inject
    public ValidDataFormat(Instance<ValidDataFormatGenerator> generators) {
        super(generators);
    }

    /**
     * Generates data for the given schema and property name. If no suitable generator is found, it uses a {@link VoidGenerator}.
     * When the schema defines a {@code pattern}, verifies that the generated value satisfies the regex using
     * ECMA-262 / JSON Schema substring semantics ({@link java.util.regex.Matcher#find()}); otherwise returns {@code null}
     * so callers fall back to pattern-driven string generation.
     *
     * @param schema       The schema for the property
     * @param propertyName The name of the property
     * @return The generated data, or {@code null} if no format generator matches the schema pattern
     */
    public Object generate(Schema<?> schema, String propertyName) {
        Object generated = super.getGenerators(schema, propertyName)
                .stream()
                .findFirst()
                .orElse(new VoidGenerator())
                .generate(schema);
        if (generated != null && schema != null && schema.getPattern() != null) {
            try {
                if (!compilePattern(schema.getPattern()).matcher(String.valueOf(generated)).find()) {
                    return null;
                }
            } catch (RuntimeException _) {
                // ignore invalid regex
            }
        }
        return generated;
    }

    private Pattern compilePattern(String regex) {
        if (patternCache.size() >= MAX_CACHED_PATTERNS) {
            patternCache.clear();
        }
        return patternCache.computeIfAbsent(regex, Pattern::compile);
    }
}
