package com.endava.cats.command.triage;

import com.endava.cats.model.RequestTarget;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Locates the request field an error message is about. Candidates are verified against the payload, the test's
 * mutation targets and (when supplied) the OpenAPI request schema, so quoted user input such as
 * {@code "Invalid sort direction 'HYI'"} or HTML attribute values are never mistaken for field names.
 */
final class ReferencedFieldExtractor {
    private static final Pattern DUPLICATE_FIELD = Pattern.compile(
            "\\bwith\\s+([A-Za-z][A-Za-z0-9_.]*)\\s+['\"][^'\"]*['\"].*\\balready\\s+exists\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPERATOR_REQUIRES_VALUE = Pattern.compile(
            "\\boperator\\s+[A-Za-z0-9_]+\\s+requires\\s+.*\\bvalue\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPRESSION_REQUIRES_CHILD = Pattern.compile(
            "\\bexpression\\s+(?:must\\s+have|requires)\\s+.*\\bchild(?:ren)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD_COLON_PREFIX = Pattern.compile("^([A-Za-z_][A-Za-z0-9_.\\[\\]]*)\\s*:\\s+\\S");
    private static final Pattern QUOTED_TOKEN = Pattern.compile("['\"`]([A-Za-z_$][A-Za-z0-9_.#$\\[\\]-]{0,80})['\"`]");
    private static final Pattern PREDICATE_FOLLOWS = Pattern.compile(
            "\\s*(?::|is\\b|are\\b|must\\b|should\\b|cannot\\b|can't\\b|may\\s+not\\b|not\\b|field\\b|property\\b|parameter\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNQUOTED_SUBJECT = Pattern.compile(
            "^([A-Za-z][A-Za-z0-9_ ]{1,40}?)\\s+(?:is|are|must|should|cannot|can't|may\\s+not)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> LEADING_STOP_WORDS = Set.of("the", "a", "an", "field", "property", "parameter", "attribute", "value", "request");

    private final RequestSchemaLookup schemaLookup;

    ReferencedFieldExtractor(RequestSchemaLookup schemaLookup) {
        this.schemaLookup = schemaLookup;
    }

    /**
     * Everything needed to resolve a referenced field for one failing test.
     */
    record Context(TriageTestCase testCase, JsonObject payload, String message, FailureIntent intent) {
    }

    Optional<String> extract(Context context) {
        return Stream.<Function<Context, Optional<String>>>of(
                        this::knownPattern,
                        this::fieldColonPrefix,
                        this::quotedCandidate,
                        this::unquotedSubject)
                .map(strategy -> strategy.apply(context))
                .flatMap(Optional::stream)
                .map(FieldNames::toDotted)
                .filter(FieldNames::isValidPath)
                .findFirst();
    }

    private Optional<String> knownPattern(Context context) {
        Matcher duplicate = DUPLICATE_FIELD.matcher(context.message());
        if (duplicate.find()) {
            return resolve(context, duplicate.group(1));
        }
        if (OPERATOR_REQUIRES_VALUE.matcher(context.message()).find()) {
            return resolve(context, "value");
        }
        return EXPRESSION_REQUIRES_CHILD.matcher(context.message()).find() ? resolve(context, "children") : Optional.empty();
    }

    private Optional<String> fieldColonPrefix(Context context) {
        Matcher matcher = FIELD_COLON_PREFIX.matcher(context.message());
        return matcher.find() ? resolve(context, matcher.group(1)).filter(field -> isPlausibleTarget(context, field)) : Optional.empty();
    }

    private Optional<String> quotedCandidate(Context context) {
        List<MatchResult> quoted = QUOTED_TOKEN.matcher(context.message()).results().toList();
        Optional<String> subject = quoted.stream()
                .filter(match -> PREDICATE_FOLLOWS.matcher(context.message()).region(match.end(), context.message().length()).lookingAt())
                .map(match -> resolve(context, match.group(1)))
                .flatMap(Optional::stream)
                .filter(field -> isPlausibleTarget(context, field))
                .findFirst();
        return subject.or(() -> quoted.stream()
                .map(match -> existingField(context, match.group(1)))
                .flatMap(Optional::stream)
                .filter(field -> isPlausibleTarget(context, field))
                .findFirst());
    }

    private Optional<String> unquotedSubject(Context context) {
        Matcher matcher = UNQUOTED_SUBJECT.matcher(context.message());
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(stripStopWords(matcher.group(1)))
                .filter(Predicate.not(String::isBlank))
                .flatMap(words -> resolve(context, words))
                .filter(field -> isPlausibleTarget(context, field));
    }

    /**
     * Resolves raw words ({@code "tax number"}, {@code "expression#operator"}) to a dotted field path: mutation
     * targets first, then existing payload fields, then contract schema properties, and finally (only when no
     * contract schema is available for the operation) a camelCase guess.
     */
    private Optional<String> resolve(Context context, String rawWords) {
        String normalized = FieldNames.normalize(FieldNames.leaf(FieldNames.toDotted(rawWords)));
        if (normalized.isEmpty() || normalized.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        return explicitPath(context, rawWords)
                .or(() -> mutationTarget(context, normalized))
                .or(() -> PayloadFields.findFieldPath(context.payload(), normalized))
                .or(() -> schemaLookup.findPropertyPath(context.testCase().path(), context.testCase().method(), normalized))
                .or(() -> camelCaseGuess(context, rawWords));
    }

    /**
     * Keeps multi-segment paths such as {@code items[0].quantity} intact (as {@code items[*].quantity}) when the
     * payload or contract confirms them, instead of matching an unrelated field with the same leaf name.
     */
    private Optional<String> explicitPath(Context context, String rawWords) {
        String dotted = FieldNames.toDotted(rawWords);
        if (!dotted.contains(".") || !FieldNames.isValidPath(dotted)) {
            return Optional.empty();
        }
        String path = context.testCase().path();
        String method = context.testCase().method();
        boolean schemaAvailable = schemaLookup.requestSchema(path, method).isPresent();
        boolean confirmed = PayloadFields.valueAt(context.payload(), dotted).isPresent()
                || schemaLookup.property(path, method, dotted).isPresent();
        return confirmed || !schemaAvailable ? Optional.of(dotted) : Optional.empty();
    }

    private Optional<String> existingField(Context context, String rawWords) {
        String normalized = FieldNames.normalize(FieldNames.leaf(FieldNames.toDotted(rawWords)));
        return mutationTarget(context, normalized).or(() -> PayloadFields.findFieldPath(context.payload(), normalized));
    }

    private static Optional<String> mutationTarget(Context context, String normalized) {
        return context.testCase().mutationTargets().stream()
                .filter(target -> target.location() == RequestTarget.Location.BODY)
                .map(target -> FieldNames.toDotted(target.name()))
                .filter(dotted -> FieldNames.normalize(FieldNames.leaf(dotted)).equals(normalized))
                .findFirst();
    }

    private Optional<String> camelCaseGuess(Context context, String rawWords) {
        if (schemaLookup.requestSchema(context.testCase().path(), context.testCase().method()).isPresent()) {
            return Optional.empty();
        }
        String trimmed = rawWords.trim();
        return trimmed.contains(" ") ? Optional.of(toCamelCase(trimmed)) : Optional.of(trimmed);
    }

    /**
     * A {@code REQUIRE} target must be missing/blank in the payload or touched by the fuzzer; a {@code REMOVE} or
     * {@code UNIQUE} target must be present. This rejects trigger fields mentioned in the message ("when 'type' is ...").
     */
    private static boolean isPlausibleTarget(Context context, String field) {
        String dotted = FieldNames.toDotted(field);
        boolean blank = PayloadFields.isBlank(PayloadFields.valueAt(context.payload(), dotted));
        if (context.intent() == FailureIntent.REQUIRE) {
            return blank || isMutationTarget(context.testCase(), dotted);
        }
        return !blank;
    }

    static boolean isMutationTarget(TriageTestCase testCase, String dottedField) {
        String plain = FieldNames.withoutWildcards(dottedField);
        return testCase.mutationTargets().stream()
                .filter(target -> target.location() == RequestTarget.Location.BODY || target.location() == RequestTarget.Location.QUERY)
                .anyMatch(target -> FieldNames.withoutWildcards(FieldNames.toDotted(target.name())).equals(plain));
    }

    private static String stripStopWords(String words) {
        List<String> tokens = List.of(words.trim().split("\\s+"));
        return tokens.stream()
                .dropWhile(token -> LEADING_STOP_WORDS.contains(token.toLowerCase(Locale.ROOT)))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private static String toCamelCase(String words) {
        String[] parts = words.split("\\s+");
        StringBuilder camel = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        Stream.of(parts).skip(1)
                .filter(Predicate.not(String::isEmpty))
                .forEach(part -> camel.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT)));
        return camel.toString();
    }
}
