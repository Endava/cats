package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.util.CatsRandom;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    private static final Map<String, Function<int[], String>> PARAMETERIZED_FUNCTIONS = Map.of(
            "alphanumeric", args -> args.length == 1 ? CatsRandom.alphanumeric(args[0]) : CatsRandom.alphanumeric(args[0], args[1]),
            "alphabetic", args -> args.length == 1 ? CatsRandom.alphabetic(args[0]) : CatsRandom.alphabetic(args[0], args[1]),
            "numeric", args -> args.length == 1 ? CatsRandom.numeric(args[0]) : CatsRandom.numeric(args[0], args[1]),
            "ascii", args -> args.length == 1 ? CatsRandom.ascii(args[0]) : CatsRandom.ascii(args[0], args[1]),
            "todayplus", args -> LocalDate.now().plusDays(args[0]).toString(),
            "todayminus", args -> LocalDate.now().minusDays(args[0]).toString()
    );

    private static final Map<String, String> NO_ARG_FUNCTIONS = Map.of(
            "uuid", UUID.class.getName(),
            "email", "email",
            "now", "now",
            "today", "today"
    );

    @Override
    public String parse(String expression, Map<String, String> context) {
        String inner = extractInner(expression);
        if (inner == null) {
            return expression;
        }

        String noArgResult = evaluateNoArgFunction(inner);
        if (noArgResult != null) {
            return noArgResult;
        }

        return evaluateParameterizedFunction(inner, expression);
    }

    private String extractInner(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("#(") && trimmed.endsWith(")")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        return null;
    }

    private String evaluateNoArgFunction(String inner) {
        String key = inner.toLowerCase();
        if (!NO_ARG_FUNCTIONS.containsKey(key)) {
            return null;
        }

        return switch (key) {
            case "uuid" -> UUID.randomUUID().toString();
            case "email" -> CatsRandom.email();
            case "now" -> OffsetDateTime.now().toString();
            case "today" -> LocalDate.now().toString();
            default -> null;
        };
    }

    private String evaluateParameterizedFunction(String inner, String originalExpression) {
        int parenOpen = inner.indexOf('(');
        if (parenOpen < 0 || !inner.endsWith(")")) {
            return originalExpression;
        }

        String funcName = inner.substring(0, parenOpen).trim().toLowerCase();
        String argsStr = inner.substring(parenOpen + 1, inner.length() - 1).trim();

        Function<int[], String> function = PARAMETERIZED_FUNCTIONS.get(funcName);
        if (function == null) {
            return originalExpression;
        }

        try {
            int[] args = parseIntArgs(argsStr);
            return function.apply(args);
        } catch (NumberFormatException e) {
            return originalExpression;
        }
    }

    private int[] parseIntArgs(String argsStr) {
        String[] parts = argsStr.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }
}
