package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Mutates the existing field value in-place while preserving its outer format structure
 * (UUID layout, ISO-8601 date/time shape, numeric type, or string length/delimiters)
 * to bypass shallow format checks and reach deeper backend logic.
 */
@Singleton
public class FormatPreservingValueMutator implements BodyMutator {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern ISO_DATE_PREFIX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}.*");
    private static final List<String> MUTATED_UUIDS = List.of(
            "00000000-0000-0000-0000-000000000000",
            "ffffffff-ffff-ffff-ffff-ffffffffffff",
            "00000000-0000-9000-fc00-000000000000");
    private static final List<String> IMPOSSIBLE_ISO_DATES = List.of(
            "2025-02-29", "2025-02-30", "2025-13-01", "2025-00-01", "0000-01-01");

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object currentValue = JsonUtils.getVariableFromJson(inputJson, selectedField);
        Object mutatedValue = mutatePreservingShape(currentValue);
        return CatsUtil.justReplaceField(inputJson, selectedField, mutatedValue).json();
    }

    static Object mutatePreservingShape(Object currentValue) {
        return switch (currentValue) {
            case Number number -> mutateNumber(number);
            case Boolean bool -> !bool;
            case String str when JsonUtils.isNotSet(str) || str.isEmpty() -> "00000000-0000-0000-0000-000000000000";
            case String str when UUID_PATTERN.matcher(str).matches() -> selectRandom(MUTATED_UUIDS);
            case String str when ISO_DATE_PREFIX.matcher(str).matches() -> selectRandom(IMPOSSIBLE_ISO_DATES) + str.substring(10);
            case String str -> mutateStructuredString(str);
            case null, default -> "00000000-0000-0000-0000-000000000000";
        };
    }

    private static Number mutateNumber(Number number) {
        long val = number.longValue();
        return switch (CatsRandom.instance().nextInt(3)) {
            case 0 -> val == 0 ? -1L : -val;
            case 1 -> val + 1L;
            default -> val - 1L;
        };
    }

    private static String mutateStructuredString(String value) {
        return switch (CatsRandom.instance().nextInt(3)) {
            case 0 -> new StringBuilder(value).reverse().toString();
            case 1 -> value + value;
            default -> flipCaseDeterministically(value);
        };
    }

    private static String flipCaseDeterministically(String value) {
        StringBuilder flipped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            flipped.append(Character.isUpperCase(ch) ? Character.toLowerCase(ch) : Character.toUpperCase(ch));
        }
        String candidate = flipped.toString();
        return candidate.equals(value) ? value + "_mutated" : candidate;
    }

    private static <T> T selectRandom(List<T> candidates) {
        return candidates.get(CatsRandom.instance().nextInt(candidates.size()));
    }

    @Override
    public String description() {
        return "mutate existing field value while preserving its outer format structure";
    }
}
