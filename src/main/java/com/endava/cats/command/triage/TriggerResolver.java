package com.endava.cats.command.triage;

import com.endava.cats.util.CatsModelUtils;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the discriminator ({@code when:} trigger) of a conditional requirement. A trigger is trusted when the error
 * message mentions its value ({@code "... must be null for DIGITAL orders"}); otherwise only an enum-like value of a
 * discriminator-named field ({@code planType: RECURRING}) that the contract (if supplied) declares as an enum qualifies.
 * Generated free-text values such as {@code name: "ZFQUSJWPHE"} are never used as triggers.
 */
final class TriggerResolver {
    private static final Set<String> DISCRIMINATOR_KEY_SUFFIXES = Set.of(
            "type", "kind", "mode", "category", "channel", "method", "operator", "status", "action", "role", "state");
    private static final Pattern ENUM_LIKE_CONSTANT = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{1,63}");

    /**
     * A resolved {@code when: "<field> == '<value>'"} trigger.
     */
    record Trigger(String field, String value) {
        String expression() {
            return "%s == '%s'".formatted(field, value);
        }
    }

    private final RequestSchemaLookup schemaLookup;

    TriggerResolver(RequestSchemaLookup schemaLookup) {
        this.schemaLookup = schemaLookup;
    }

    Optional<Trigger> resolve(TriageTestCase testCase, JsonObject payload, String message, String targetField) {
        List<PayloadFields.PrimitiveField> candidates = PayloadFields.primitiveFields(payload).stream()
                .filter(field -> !FieldNames.overlaps(field.path(), targetField))
                .filter(field -> SAFE_VALUE.matcher(field.value().getAsString()).matches())
                .toList();
        return candidates.stream()
                .map(field -> mentionedTrigger(testCase, field, message))
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(field -> isDeclaredDiscriminator(testCase, field))
                        .map(field -> new Trigger(field.path(), field.value().getAsString()))
                        .findFirst());
    }

    private Optional<Trigger> mentionedTrigger(TriageTestCase testCase, PayloadFields.PrimitiveField field, String message) {
        if (!isDiscriminatorCandidate(field) || field.value().isNumber() || field.value().isBoolean()) {
            return Optional.empty();
        }
        Matcher mention = Pattern.compile("\\b" + Pattern.quote(field.value().getAsString()) + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(message);
        return mention.find()
                ? Optional.of(new Trigger(field.path(), canonicalValue(testCase, field.path(), mention.group())))
                : Optional.empty();
    }

    private boolean isDeclaredDiscriminator(TriageTestCase testCase, PayloadFields.PrimitiveField field) {
        String value = field.value().getAsString();
        if (!hasDiscriminatorSuffix(field.key()) || !ENUM_LIKE_CONSTANT.matcher(value).matches()) {
            return false;
        }
        return schemaLookup.requestSchema(testCase.path(), testCase.method()).isEmpty()
                || enumValues(testCase, field.path()).stream().anyMatch(enumValue -> enumValue.equalsIgnoreCase(value));
    }

    private String canonicalValue(TriageTestCase testCase, String fieldPath, String mentioned) {
        return enumValues(testCase, fieldPath).stream()
                .filter(enumValue -> enumValue.equalsIgnoreCase(mentioned))
                .findFirst()
                .orElse(mentioned);
    }

    private List<String> enumValues(TriageTestCase testCase, String fieldPath) {
        return schemaLookup.property(testCase.path(), testCase.method(), fieldPath)
                .filter(CatsModelUtils::isEnumSchema)
                .map(Schema::getEnum)
                .map(values -> values.stream().filter(Objects::nonNull).map(String::valueOf).toList())
                .orElse(List.of());
    }

    private static boolean isDiscriminatorCandidate(PayloadFields.PrimitiveField field) {
        return hasDiscriminatorSuffix(field.key()) || ENUM_LIKE_CONSTANT.matcher(field.value().getAsString()).matches();
    }

    private static boolean hasDiscriminatorSuffix(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return DISCRIMINATOR_KEY_SUFFIXES.stream().anyMatch(lower::endsWith);
    }
}
