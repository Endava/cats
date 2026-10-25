package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.util.CatsRandom;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Parser for shorthand function expressions using the {@code #(functionName(args))} syntax.
 * <p>
 * Provides convenient aliases for common random data generation functions,
 * removing the need for verbose {@code T(...)} expressions.
 * <p>
 * Supported functions:
 * <ul>
 *     <li>{@code #(alphanumeric(N))} or {@code #(alphanumeric(min,max))} - random alphanumeric string</li>
 *     <li>{@code #(alphabetic(N))} or {@code #(alphabetic(min,max))} - random alphabetic string</li>
 *     <li>{@code #(numeric(N))} or {@code #(numeric(min,max))} - random numeric string</li>
 *     <li>{@code #(ascii(N))} or {@code #(ascii(min,max))} - random ascii string</li>
 *     <li>{@code #(uuid)} - random UUID</li>
 *     <li>{@code #(email)} - random email address</li>
 *     <li>{@code #(now)} - current OffsetDateTime</li>
 *     <li>{@code #(today)} - current LocalDate</li>
 *     <li>{@code #(todayPlus(N))} - current date plus N days</li>
 *     <li>{@code #(todayMinus(N))} - current date minus N days</li>
 * </ul>
 */
public class ShorthandFunctionParser implements Parser {

    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String[] ISO_COUNTRIES = Locale.getISOCountries();

    private static final Map<String, Function<int[], String>> PARAMETERIZED_FUNCTIONS = Map.ofEntries(
            Map.entry("alphanumeric", args -> args.length == 1 ? CatsRandom.alphanumeric(args[0]) : CatsRandom.alphanumeric(args[0], args[1])),
            Map.entry("alphabetic", args -> args.length == 1 ? CatsRandom.alphabetic(args[0]) : CatsRandom.alphabetic(args[0], args[1])),
            Map.entry("numeric", args -> args.length == 1 ? CatsRandom.numeric(args[0]) : CatsRandom.numeric(args[0], args[1])),
            Map.entry("ascii", args -> args.length == 1 ? CatsRandom.ascii(args[0]) : CatsRandom.ascii(args[0], args[1])),
            Map.entry("todayplus", args -> LocalDate.now(ZoneOffset.UTC).plusDays(args[0]).toString()),
            Map.entry("todayminus", args -> LocalDate.now(ZoneOffset.UTC).minusDays(args[0]).toString()),
            Map.entry("nowplusdays", args -> OffsetDateTime.now(ZoneOffset.UTC).plusDays(args[0]).toString()),
            Map.entry("nowminusdays", args -> OffsetDateTime.now(ZoneOffset.UTC).minusDays(args[0]).toString()),
            Map.entry("nowplushours", args -> OffsetDateTime.now(ZoneOffset.UTC).plusHours(args[0]).toString()),
            Map.entry("nowminushours", args -> OffsetDateTime.now(ZoneOffset.UTC).minusHours(args[0]).toString()),
            Map.entry("randomint", args -> String.valueOf( args.length == 1
                    ? CatsRandom.instance().nextInt(Math.max(1, args[0]))
                    : args[0] + CatsRandom.instance().nextInt(Math.max(1, args[1] - args[0]))))
    );

    private static final Map<String, String> NO_ARG_FUNCTIONS = Map.of(
            "uuid", UUID.class.getName(),
            "ulid", "ulid",
            "email", "email",
            "now", "now",
            "today", "today",
            "country", "country",
            "countrycode", "countrycode"
    );

    @Override
    public String parse(String expression, Map<String, String> context) {
        String inner = extractInner(expression);
        if (inner != null) {
            return evaluateSingleToken(inner, expression);
        }
        return replaceEmbeddedShorthands(expression);
    }

    private String evaluateSingleToken(String inner, String originalExpression) {
        String noArgResult = evaluateNoArgFunction(inner);
        if (noArgResult != null) {
            return noArgResult;
        }
        return evaluateParameterizedFunction(inner, originalExpression);
    }

    private String replaceEmbeddedShorthands(String expression) {
        if (expression == null || !expression.contains("#(")) {
            return expression;
        }
        StringBuilder out = new StringBuilder();
        int idx = 0;
        while (idx < expression.length()) {
            int start = expression.indexOf("#(", idx);
            int end = start >= 0 ? findMatchingClosingParen(expression, start + 2) : -1;
            if (start < 0 || end < 0) {
                out.append(expression, idx, expression.length());
                break;
            }
            out.append(expression, idx, start);
            String token = expression.substring(start, end + 1);
            String inner = expression.substring(start + 2, end).trim();
            out.append(evaluateSingleToken(inner, token));
            idx = end + 1;
        }
        return out.toString();
    }

    private static int findMatchingClosingParen(String text, int startAfterOpen) {
        int depth = 1;
        for (int i = startAfterOpen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String extractInner(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("#(") && trimmed.endsWith(")")
                && findMatchingClosingParen(trimmed, 2) == trimmed.length() - 1) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        return null;
    }

    private String evaluateNoArgFunction(String inner) {
        String key = inner.toLowerCase(Locale.ROOT);
        if (!NO_ARG_FUNCTIONS.containsKey(key)) {
            return null;
        }

        return switch (key) {
            case "uuid" -> CatsRandom.uuid();
            case "ulid" -> generateUlid();
            case "email" -> CatsRandom.email();
            case "now" -> OffsetDateTime.now(ZoneOffset.UTC).toString();
            case "today" -> LocalDate.now(ZoneOffset.UTC).toString();
            case "country", "countrycode" -> ISO_COUNTRIES[CatsRandom.instance().nextInt(ISO_COUNTRIES.length)];
            default -> null;
        };
    }

    private static String generateUlid() {
        long timestamp = System.currentTimeMillis();
        char[] chars = new char[26];
        for (int i = 9; i >= 0; i--) {
            chars[i] = CROCKFORD_BASE32.charAt((int) (timestamp & 31));
            timestamp >>>= 5;
        }
        for (int i = 10; i < 26; i++) {
            chars[i] = CROCKFORD_BASE32.charAt(CatsRandom.instance().nextInt(32));
        }
        return new String(chars);
    }

    private String evaluateParameterizedFunction(String inner, String originalExpression) {
        int parenOpen = inner.indexOf('(');
        if (parenOpen < 0 || !inner.endsWith(")")) {
            return originalExpression;
        }

        String funcName = inner.substring(0, parenOpen).trim().toLowerCase(Locale.ROOT);
        String argsStr = inner.substring(parenOpen + 1, inner.length() - 1).trim();

        Function<int[], String> function = PARAMETERIZED_FUNCTIONS.get(funcName);
        if (function == null) {
            return originalExpression;
        }

        try {
            int[] args = parseIntArgs(argsStr);
            return function.apply(args);
        } catch (NumberFormatException _) {
            return originalExpression;
        }
    }

    private int[] parseIntArgs(String argsStr) {
        String[] parts = argsStr.split(",", -1);
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }
}
