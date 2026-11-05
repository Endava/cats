package com.endava.cats.command.triage;

import com.endava.cats.util.CatsModelUtils;
import com.google.gson.JsonElement;
import io.swagger.v3.oas.models.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Produces typed {@code set:} placeholder values for synthesized correlation rules: from the OpenAPI property schema
 * when available, otherwise from type hints in the error message ({@code "requires a numeric value"}), otherwise from
 * the type of the value present in the failing request, falling back to {@code #(alphanumeric(8))}.
 */
final class PlaceholderValues {
    static final String DEFAULT_STRING = "#(alphanumeric(8))";
    private static final int DEFAULT_STRING_LENGTH = 8;
    private static final Pattern NUMERIC_HINT = Pattern.compile("\\b(?:numeric|number|integer|decimal)\\b");
    private static final Pattern BOOLEAN_HINT = Pattern.compile("\\bboolean\\b");
    private static final Pattern LIST_HINT = Pattern.compile("\\b(?:array|list|children|at\\s+least\\s+one)\\b");

    private final RequestSchemaLookup schemaLookup;

    PlaceholderValues(RequestSchemaLookup schemaLookup) {
        this.schemaLookup = schemaLookup;
    }

    Object forRequirement(TriageTestCase testCase, String field, String lowerMessage, Optional<JsonElement> currentValue) {
        return schemaLookup.property(testCase.path(), testCase.method(), field)
                .flatMap(this::fromSchema)
                .or(() -> fromMessage(lowerMessage, currentValue))
                .or(() -> currentValue.flatMap(PlaceholderValues::fromCurrentValue))
                .orElse(DEFAULT_STRING);
    }

    /**
     * Returns a unique-value generator for string fields; numeric/boolean/structured fields cannot be made unique
     * with correlation placeholders (dynamic functions always produce strings), so they yield empty.
     */
    Optional<Object> forUniqueness(TriageTestCase testCase, String field) {
        Optional<Schema<?>> schema = schemaLookup.property(testCase.path(), testCase.method(), field);
        if (schema.isPresent() && !CatsModelUtils.isStringSchema(schema.get())) {
            return Optional.empty();
        }
        return Optional.of(schema.map(PlaceholderValues::boundedAlphanumeric).orElse(DEFAULT_STRING));
    }

    private Optional<Object> fromSchema(Schema<?> schema) {
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return schema.getEnum().stream().filter(Objects::nonNull).findFirst().map(Object.class::cast);
        }
        if (CatsModelUtils.isBooleanSchema(schema)) {
            return Optional.of(true);
        }
        if (CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema)) {
            return Optional.of(boundedNumber(schema));
        }
        if (CatsModelUtils.isArraySchema(schema)) {
            Object item = Optional.ofNullable(schema.getItems()).map(schemaLookup::resolve).flatMap(this::fromSchema).orElse(DEFAULT_STRING);
            return Optional.of(List.of(item));
        }
        if (CatsModelUtils.isStringSchema(schema)) {
            return Optional.of(stringForFormat(schema));
        }
        return schemaLookup.properties(schema).isEmpty() ? Optional.empty() : Optional.of(Map.of());
    }

    private static Optional<Object> fromMessage(String lowerMessage, Optional<JsonElement> currentValue) {
        if (LIST_HINT.matcher(lowerMessage).find()) {
            return Optional.of(List.of(currentValue.flatMap(PlaceholderValues::firstArrayItem).orElse(DEFAULT_STRING)));
        }
        if (NUMERIC_HINT.matcher(lowerMessage).find()) {
            return Optional.of(1);
        }
        return BOOLEAN_HINT.matcher(lowerMessage).find() ? Optional.of(true) : Optional.empty();
    }

    private static Optional<Object> fromCurrentValue(JsonElement value) {
        if (value.isJsonArray()) {
            return Optional.of(List.of(firstArrayItem(value).orElse(DEFAULT_STRING)));
        }
        if (value.isJsonObject()) {
            return Optional.of(Map.of());
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return Optional.of(1);
        }
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? Optional.of(true) : Optional.empty();
    }

    private static Optional<Object> firstArrayItem(JsonElement value) {
        return value.isJsonArray() && !value.getAsJsonArray().isEmpty()
                ? Optional.of(value.getAsJsonArray().get(0).deepCopy())
                : Optional.empty();
    }

    private static Number boundedNumber(Schema<?> schema) {
        BigDecimal candidate = Optional.ofNullable(schema.getMinimum())
                .map(min -> Boolean.TRUE.equals(schema.getExclusiveMinimum()) ? min.add(BigDecimal.ONE) : min)
                .or(() -> Optional.ofNullable(schema.getExclusiveMinimumValue()).map(min -> min.add(BigDecimal.ONE)))
                .map(min -> min.max(BigDecimal.ONE))
                .orElse(BigDecimal.ONE);
        BigDecimal bounded = Optional.ofNullable(schema.getMaximum()).map(candidate::min).orElse(candidate);
        return CatsModelUtils.isIntegerSchema(schema) ? bounded.toBigInteger() : bounded;
    }

    private static String stringForFormat(Schema<?> schema) {
        String format = Optional.ofNullable(schema.getFormat()).orElse("").toLowerCase(Locale.ROOT);
        return switch (format) {
            case "email" -> "#(email)";
            case "uuid" -> "#(uuid)";
            case "date" -> "#(today)";
            case "date-time" -> "#(now)";
            default -> boundedAlphanumeric(schema);
        };
    }

    private static String boundedAlphanumeric(Schema<?> schema) {
        int min = Optional.ofNullable(schema.getMinLength()).orElse(1);
        int max = Optional.ofNullable(schema.getMaxLength()).orElse(Integer.MAX_VALUE);
        int length = Math.max(min, Math.min(DEFAULT_STRING_LENGTH, max));
        return "#(alphanumeric(%d))".formatted(length);
    }
}
