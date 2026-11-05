package com.endava.cats.command.triage;

import com.endava.cats.fuzzer.fields.OnlyRequiredFieldsFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.model.RequestTarget;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Analyzes rejections of requests that CATS expected to succeed ({@code HappyPath}, {@code OnlyRequiredFields}, or
 * any test whose expected result is {@code 2XX} only). Negative tests expecting {@code 4XX} are never analyzed, so a
 * legitimate rejection cannot be turned into a bogus correlation rule.
 */
final class FailureAnalyzer {
    private static final Set<Integer> REJECTION_CODES = Set.of(400, 409, 422);

    private final ReferencedFieldExtractor fieldExtractor;

    FailureAnalyzer(RequestSchemaLookup schemaLookup) {
        this.fieldExtractor = new ReferencedFieldExtractor(schemaLookup);
    }

    Optional<FailureAnalysis> analyze(TriageTestCase testCase) {
        if (!REJECTION_CODES.contains(testCase.statusCode()) || !expectedToSucceed(testCase)) {
            return Optional.empty();
        }
        return testCase.payloadRootObject().map(payload -> analyze(testCase, payload, ErrorMessage.of(testCase)));
    }

    static boolean expectedToSucceed(TriageTestCase testCase) {
        return testCase.isFuzzer(HappyPathFuzzer.class)
                || testCase.isFuzzer(OnlyRequiredFieldsFuzzer.class)
                || testCase.expected().expectsOnly2xx();
    }

    private FailureAnalysis analyze(TriageTestCase testCase, JsonObject payload, ErrorMessage message) {
        Optional<FailureIntent> intent = message.html() ? Optional.empty() : FailureIntent.of(message.lower());
        Optional<String> field = intent.flatMap(i -> fieldExtractor.extract(
                new ReferencedFieldExtractor.Context(testCase, payload, message.clean(), i)));
        return new FailureAnalysis(message, payload, intent, field, protectedField(testCase, payload, field, message.clean()));
    }

    /**
     * When the message names a field, it is protected only if the fuzzer mutated it to a present value; when it names
     * none, a mutated field mentioned by name or quoted value is the rejected one.
     */
    private static Optional<String> protectedField(TriageTestCase testCase, JsonObject payload, Optional<String> field, String message) {
        if (field.isPresent()) {
            return field.filter(f -> isPresentMutationTarget(testCase, payload, f));
        }
        return mentionedMutationTarget(testCase, payload, message);
    }

    private static boolean isPresentMutationTarget(TriageTestCase testCase, JsonObject payload, String field) {
        return ReferencedFieldExtractor.isMutationTarget(testCase, field)
                && !PayloadFields.isBlank(PayloadFields.valueAt(payload, field));
    }

    /**
     * A mutated field with a present value whose name or sent value is quoted/mentioned in the error message
     * (e.g. {@code "Unknown country 'AA'"} after {@code MinLengthExactValuesInStringFields} sent {@code country: "AA"}).
     */
    private static Optional<String> mentionedMutationTarget(TriageTestCase testCase, JsonObject payload, String message) {
        return testCase.mutationTargets().stream()
                .filter(target -> target.location() == RequestTarget.Location.BODY)
                .map(target -> FieldNames.toDotted(target.name()))
                .filter(field -> isMentioned(message, field, PayloadFields.valueAt(payload, field)))
                .findFirst();
    }

    private static boolean isMentioned(String message, String field, Optional<JsonElement> value) {
        return value.filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .filter(Predicate.not(String::isBlank))
                .map(sent -> containsWord(message, FieldNames.leaf(field)) || containsQuoted(message, sent))
                .orElse(false);
    }

    private static boolean containsWord(String message, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE).matcher(message).find();
    }

    private static boolean containsQuoted(String message, String value) {
        return message.contains("'" + value + "'") || message.contains("\"" + value + "\"");
    }
}
