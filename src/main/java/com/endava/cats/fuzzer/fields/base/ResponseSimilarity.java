package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.google.common.base.Splitter;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Computes structural and content similarity in {@code [0.0, 1.0]} between two HTTP responses after stripping
 * reflected inputs and normalizing volatile timestamps, modelled after OWASP ZAP's {@code ComparableResponse}
 * and sqlmap's JSON-aware {@code comparison.py}.
 */
public final class ResponseSimilarity {
    public static final double SIMILAR_THRESHOLD = 0.90;
    public static final double DIFFERENT_THRESHOLD = 0.75;

    private static final double WORD_WEIGHT = 0.75;
    private static final double LINE_WEIGHT = 0.15;
    private static final double LENGTH_WEIGHT = 0.10;

    private static final Set<String> VOLATILE_JSON_KEYS = Set.of(
            "timestamp", "traceid", "requestid", "correlationid", "spanid", "x-request-id");
    private static final Pattern ISO_TIMESTAMP = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?");
    private static final Splitter LINE_SPLITTER = Splitter.on(Pattern.compile("\r\n|\r|\n"));
    private static final Splitter WORD_SPLITTER = Splitter.on(Pattern.compile("[^a-zA-Z0-9_]+")).omitEmptyStrings();

    private ResponseSimilarity() {
    }

    /**
     * Computes similarity in {@code [0.0, 1.0]} between {@code first} and {@code second}.
     *
     * @param first  first HTTP response
     * @param second second HTTP response
     * @param inputs values sent in each request and the unfuzzed original value to strip before comparison
     * @return {@code 1.0} when responses are equivalent, {@code 0.0} when completely different
     */
    public static double similarity(CatsResponse first, CatsResponse second, ComparisonInputs inputs) {
        if (first == null || second == null || first.getResponseCode() != second.getResponseCode()) {
            return 0.0;
        }
        if (isRedirectMismatch(first, second)) {
            return 0.0;
        }
        String bodyA = normalizeBody(first, inputs.firstSent(), inputs.secondSent(), inputs.originalValue());
        String bodyB = normalizeBody(second, inputs.firstSent(), inputs.secondSent(), inputs.originalValue());
        if (bodyA.isEmpty() && bodyB.isEmpty()) {
            return 1.0;
        }
        if (bodyA.isEmpty() || bodyB.isEmpty()) {
            return 0.0;
        }
        return jsonSimilarity(bodyA, bodyB).orElseGet(() -> textSimilarity(bodyA, bodyB));
    }

    private static boolean isRedirectMismatch(CatsResponse first, CatsResponse second) {
        int code = first.getResponseCode();
        if (code < 300 || code >= 400) {
            return false;
        }
        return !Objects.equals(headerValue(first, HttpHeaders.LOCATION), headerValue(second, HttpHeaders.LOCATION));
    }

    private static String headerValue(CatsResponse response, String name) {
        KeyValuePair<String, String> header = response.getHeader(name);
        return header == null ? null : header.getValue();
    }

    private static String normalizeBody(CatsResponse response, String firstSent, String secondSent, String originalValue) {
        CatsResponse stripped = Stream.of(firstSent, secondSent, originalValue)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .reduce(response, PayloadReflections::strip, (first, _) -> first);
        String raw = StringUtils.defaultString(stripped.getBody()).trim();
        return ISO_TIMESTAMP.matcher(raw).replaceAll("<TS>");
    }

    private static Optional<Double> jsonSimilarity(String bodyA, String bodyB) {
        if (!looksLikeJsonContainer(bodyA) || !looksLikeJsonContainer(bodyB)) {
            return Optional.empty();
        }
        try {
            JsonElement elemA = JsonUtils.JSON_STRICT_PARSER.fromJson(bodyA, JsonElement.class);
            JsonElement elemB = JsonUtils.JSON_STRICT_PARSER.fromJson(bodyB, JsonElement.class);
            if (elemA == null || elemB == null || elemA.isJsonPrimitive() || elemB.isJsonPrimitive()) {
                return Optional.empty();
            }
            Map<String, Integer> tokensA = new LinkedHashMap<>();
            Map<String, Integer> tokensB = new LinkedHashMap<>();
            collectJsonTokens("$", elemA, tokensA);
            collectJsonTokens("$", elemB, tokensB);
            return Optional.of(multisetDice(tokensA, tokensB));
        } catch (RuntimeException _) {
            return Optional.empty();
        }
    }

    private static boolean looksLikeJsonContainer(String text) {
        char first = text.charAt(0);
        return first == '{' || first == '[';
    }

    private static void collectJsonTokens(String path, JsonElement element, Map<String, Integer> bag) {
        if (element == null || element.isJsonNull()) {
            increment(bag, path + "=null");
        } else if (element instanceof JsonPrimitive primitive) {
            increment(bag, path + "=" + primitiveToken(primitive));
        } else if (element instanceof JsonArray array) {
            increment(bag, path + "#len=" + array.size());
            for (JsonElement item : array) {
                collectJsonTokens(path + "[]", item, bag);
            }
        } else if (element instanceof JsonObject object) {
            collectObjectTokens(path, object, bag);
        }
    }

    private static void collectObjectTokens(String path, JsonObject object, Map<String, Integer> bag) {
        if (object.isEmpty()) {
            increment(bag, path + "={}");
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String normalizedKey = entry.getKey().toLowerCase(Locale.ROOT).replace("_", "");
            if (!VOLATILE_JSON_KEYS.contains(normalizedKey)) {
                collectJsonTokens(path + "." + entry.getKey(), entry.getValue(), bag);
            }
        }
    }

    private static String primitiveToken(JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return "bool:" + primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return "num:" + primitive.getAsBigDecimal().stripTrailingZeros().toPlainString();
        }
        return "str:" + primitive.getAsString();
    }

    private static double textSimilarity(String bodyA, String bodyB) {
        if (bodyA.equals(bodyB)) {
            return 1.0;
        }
        double wordSim = multisetDice(wordMultiset(bodyA), wordMultiset(bodyB));
        double lineSim = ratio(LINE_SPLITTER.splitToList(bodyA).size(), LINE_SPLITTER.splitToList(bodyB).size());
        double lengthSim = ratio(bodyA.length(), bodyB.length());
        return WORD_WEIGHT * wordSim + LINE_WEIGHT * lineSim + LENGTH_WEIGHT * lengthSim;
    }

    private static Map<String, Integer> wordMultiset(String text) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String token : WORD_SPLITTER.split(text.toLowerCase(Locale.ROOT))) {
            increment(counts, token);
        }
        return counts;
    }

    private static double multisetDice(Map<String, Integer> first, Map<String, Integer> second) {
        int totalFirst = first.values().stream().mapToInt(Integer::intValue).sum();
        int totalSecond = second.values().stream().mapToInt(Integer::intValue).sum();
        if (totalFirst == 0 && totalSecond == 0) {
            return 1.0;
        }
        if (totalFirst == 0 || totalSecond == 0) {
            return 0.0;
        }
        int overlap = first.entrySet().stream()
                .mapToInt(entry -> Math.min(entry.getValue(), second.getOrDefault(entry.getKey(), 0)))
                .sum();
        return (2.0 * overlap) / (totalFirst + totalSecond);
    }

    private static double ratio(int first, int second) {
        int max = Math.max(first, second);
        return max == 0 ? 1.0 : (double) Math.min(first, second) / max;
    }

    private static void increment(Map<String, Integer> bag, String key) {
        bag.merge(key, 1, Integer::sum);
    }

    /**
     * Input values associated with the two compared responses.
     *
     * @param firstSent     payload value sent for the first response
     * @param secondSent    payload value sent for the second response
     * @param originalValue unfuzzed field value
     */
    public record ComparisonInputs(String firstSent, String secondSent, String originalValue) {
    }
}
