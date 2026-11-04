package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.util.CatsRandom;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Parser for shorthand function expressions using the {@code #(functionName(args))} syntax.
 * <p>
 * Provides convenient aliases for common random data generation and string transformation functions,
 * removing the need for verbose {@code T(...)} expressions.
 * <p>
 * Supported functions include:
 * <ul>
 *     <li>{@code #(alphanumeric(N))} or {@code #(alphanumeric(min,max))} - random alphanumeric string</li>
 *     <li>{@code #(alphabetic(N))} or {@code #(alphabetic(min,max))} - random alphabetic string</li>
 *     <li>{@code #(numeric(N))} or {@code #(numeric(min,max))} - random numeric string</li>
 *     <li>{@code #(ascii(N))} or {@code #(ascii(min,max))} - random ascii string</li>
 *     <li>{@code #(hex(N))} or {@code #(hex(min,max))} - random hexadecimal string</li>
 *     <li>{@code #(upper(expr))}, {@code #(lower(expr))}, {@code #(capitalize(expr))}, {@code #(trim(expr))}, {@code #(reverse(expr))}, {@code #(length(expr))} - composable string helpers</li>
 *     <li>{@code #(base64(expr))}, {@code #(base64Url(expr))}, {@code #(sha256(expr))}, {@code #(md5(expr))}, {@code #(urlEncode(expr))} - encoding and hashing helpers</li>
 *     <li>{@code #(pick(v1,v2,...))} or {@code #(oneOf(v1,v2,...))} - random selection from candidates</li>
 *     <li>{@code #(uuid)}, {@code #(ulid)}, {@code #(email)}, {@code #(bool)}, {@code #(epoch)}, {@code #(epochMillis)}</li>
 *     <li>{@code #(now)}, {@code #(today)}, {@code #(todayPlus(N))}, {@code #(todayMinus(N))}</li>
 * </ul>
 */
public class ShorthandFunctionParser implements Parser {

    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String HEX_CHARS = "0123456789abcdef";
    private static final String[] ISO_COUNTRIES = Locale.getISOCountries();

    private static final int DEFAULT_RANDOM_LENGTH = 12;
    private static final Set<String> PICK_FUNCTIONS = Set.of("pick", "oneof");

    private static final Map<String, Function<int[], String>> PARAMETERIZED_FUNCTIONS = Map.ofEntries(
            Map.entry("alphanumeric", args -> args.length == 1 ? CatsRandom.alphanumeric(args[0]) : CatsRandom.alphanumeric(args[0], args[1])),
            Map.entry("alphabetic", args -> args.length == 1 ? CatsRandom.alphabetic(args[0]) : CatsRandom.alphabetic(args[0], args[1])),
            Map.entry("alpha", args -> args.length == 1 ? CatsRandom.alphabetic(args[0]) : CatsRandom.alphabetic(args[0], args[1])),
            Map.entry("numeric", args -> args.length == 1 ? CatsRandom.numeric(args[0]) : CatsRandom.numeric(args[0], args[1])),
            Map.entry("ascii", args -> args.length == 1 ? CatsRandom.ascii(args[0]) : CatsRandom.ascii(args[0], args[1])),
            Map.entry("hex", args -> args.length == 1 ? generateHex(args[0]) : generateHexRange(args[0], args[1])),
            Map.entry("todayplus", args -> LocalDate.now(ZoneOffset.UTC).plusDays(args[0]).toString()),
            Map.entry("todayminus", args -> LocalDate.now(ZoneOffset.UTC).minusDays(args[0]).toString()),
            Map.entry("nowplusdays", args -> OffsetDateTime.now(ZoneOffset.UTC).plusDays(args[0]).toString()),
            Map.entry("nowminusdays", args -> OffsetDateTime.now(ZoneOffset.UTC).minusDays(args[0]).toString()),
            Map.entry("nowplushours", args -> OffsetDateTime.now(ZoneOffset.UTC).plusHours(args[0]).toString()),
            Map.entry("nowminushours", args -> OffsetDateTime.now(ZoneOffset.UTC).minusHours(args[0]).toString()),
            Map.entry("randomint", args -> String.valueOf(args.length == 1
                    ? CatsRandom.instance().nextInt(Math.max(1, args[0]))
                    : args[0] + CatsRandom.instance().nextInt(Math.max(1, args[1] - args[0]))))
    );

    private static final Map<String, UnaryOperator<String>> STRING_FUNCTIONS = Map.ofEntries(
            Map.entry("upper", s -> s.toUpperCase(Locale.ROOT)),
            Map.entry("uppercase", s -> s.toUpperCase(Locale.ROOT)),
            Map.entry("lower", s -> s.toLowerCase(Locale.ROOT)),
            Map.entry("lowercase", s -> s.toLowerCase(Locale.ROOT)),
            Map.entry("capitalize", ShorthandFunctionParser::capitalize),
            Map.entry("trim", String::trim),
            Map.entry("reverse", s -> new StringBuilder(s).reverse().toString()),
            Map.entry("length", s -> String.valueOf(s.length())),
            Map.entry("len", s -> String.valueOf(s.length())),
            Map.entry("base64", s -> Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8))),
            Map.entry("base64url", s -> Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8))),
            Map.entry("sha256", s -> digestHex("SHA-256", s)),
            Map.entry("md5", s -> digestHex("MD5", s)),
            Map.entry("urlencode", s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
    );

    private static final Map<String, Supplier<String>> NO_ARG_FUNCTIONS = Map.ofEntries(
            Map.entry("uuid", CatsRandom::uuid),
            Map.entry("ulid", ShorthandFunctionParser::generateUlid),
            Map.entry("email", CatsRandom::email),
            Map.entry("now", () -> OffsetDateTime.now(ZoneOffset.UTC).toString()),
            Map.entry("today", () -> LocalDate.now(ZoneOffset.UTC).toString()),
            Map.entry("epoch", () -> String.valueOf(Instant.now().getEpochSecond())),
            Map.entry("epochseconds", () -> String.valueOf(Instant.now().getEpochSecond())),
            Map.entry("timestamp", () -> String.valueOf(Instant.now().getEpochSecond())),
            Map.entry("epochmillis", () -> String.valueOf(System.currentTimeMillis())),
            Map.entry("timestampmillis", () -> String.valueOf(System.currentTimeMillis())),
            Map.entry("bool", () -> String.valueOf(CatsRandom.instance().nextBoolean())),
            Map.entry("boolean", () -> String.valueOf(CatsRandom.instance().nextBoolean())),
            Map.entry("randomboolean", () -> String.valueOf(CatsRandom.instance().nextBoolean())),
            Map.entry("country", () -> ISO_COUNTRIES[CatsRandom.instance().nextInt(ISO_COUNTRIES.length)]),
            Map.entry("countrycode", () -> ISO_COUNTRIES[CatsRandom.instance().nextInt(ISO_COUNTRIES.length)]),
            Map.entry("alphanumeric", () -> CatsRandom.alphanumeric(DEFAULT_RANDOM_LENGTH)),
            Map.entry("alphabetic", () -> CatsRandom.alphabetic(DEFAULT_RANDOM_LENGTH)),
            Map.entry("alpha", () -> CatsRandom.alphabetic(DEFAULT_RANDOM_LENGTH)),
            Map.entry("upper", () -> CatsRandom.alphabetic(DEFAULT_RANDOM_LENGTH).toUpperCase(Locale.ROOT)),
            Map.entry("uppercase", () -> CatsRandom.alphabetic(DEFAULT_RANDOM_LENGTH).toUpperCase(Locale.ROOT)),
            Map.entry("lower", () -> CatsRandom.alphabetic(DEFAULT_RANDOM_LENGTH).toLowerCase(Locale.ROOT)),
            Map.entry("lowercase", () -> CatsRandom.alphabetic(DEFAULT_RANDOM_LENGTH).toLowerCase(Locale.ROOT)),
            Map.entry("numeric", () -> CatsRandom.numeric(DEFAULT_RANDOM_LENGTH)),
            Map.entry("ascii", () -> CatsRandom.ascii(DEFAULT_RANDOM_LENGTH)),
            Map.entry("hex", () -> generateHex(DEFAULT_RANDOM_LENGTH))
    );

    @Override
    public String parse(String expression, Map<String, String> context) {
        String inner = extractInner(expression);
        if (inner != null) {
            return evaluateSingleToken(inner, expression, context);
        }
        return replaceEmbeddedShorthands(expression, context);
    }

    private String evaluateSingleToken(String inner, String originalExpression, Map<String, String> context) {
        String noArgResult = evaluateNoArgFunction(inner);
        if (noArgResult != null) {
            return noArgResult;
        }
        return evaluateParameterizedFunction(inner, originalExpression, context);
    }

    private String replaceEmbeddedShorthands(String expression, Map<String, String> context) {
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
            out.append(evaluateSingleToken(inner, token, context));
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
        Supplier<String> supplier = NO_ARG_FUNCTIONS.get(inner.toLowerCase(Locale.ROOT));
        return supplier != null ? supplier.get() : null;
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

    private static String generateHex(int length) {
        char[] chars = new char[Math.max(0, length)];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = HEX_CHARS.charAt(CatsRandom.instance().nextInt(HEX_CHARS.length()));
        }
        return new String(chars);
    }

    private static String generateHexRange(int minInclusive, int maxExclusive) {
        int length = minInclusive >= maxExclusive
                ? Math.max(0, minInclusive)
                : minInclusive + CatsRandom.instance().nextInt(maxExclusive - minInclusive);
        return generateHex(length);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String digestHex(String algorithm, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing digest algorithm: " + algorithm, e);
        }
    }

    private String evaluateParameterizedFunction(String inner, String originalExpression, Map<String, String> context) {
        int parenOpen = inner.indexOf('(');
        if (parenOpen < 0 || findMatchingClosingParen(inner, parenOpen + 1) != inner.length() - 1) {
            return originalExpression;
        }

        String funcName = inner.substring(0, parenOpen).trim().toLowerCase(Locale.ROOT);
        String argsStr = inner.substring(parenOpen + 1, inner.length() - 1).trim();

        if (PICK_FUNCTIONS.contains(funcName)) {
            return evaluatePickFunction(argsStr, originalExpression, context);
        }
        UnaryOperator<String> stringFunc = STRING_FUNCTIONS.get(funcName);
        if (stringFunc != null) {
            return argsStr.isEmpty() ? originalExpression : stringFunc.apply(resolveArgument(argsStr, context));
        }
        return evaluateNumericFunction(funcName, argsStr, originalExpression);
    }

    private String evaluatePickFunction(String argsStr, String originalExpression, Map<String, String> context) {
        if (argsStr.isEmpty()) {
            return originalExpression;
        }
        List<String> options = splitTopLevelCommas(argsStr);
        String chosen = options.get(CatsRandom.instance().nextInt(options.size()));
        return resolveArgument(chosen, context);
    }

    private String resolveArgument(String arg, Map<String, String> context) {
        String trimmed = arg.trim();
        if (isQuoted(trimmed)) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        String unwrapped = extractInner(trimmed);
        if (unwrapped != null) {
            return evaluateSingleToken(unwrapped, trimmed, context);
        }
        if (trimmed.contains("#(")) {
            return replaceEmbeddedShorthands(trimmed, context);
        }
        if (context != null && context.containsKey(trimmed)) {
            return context.get(trimmed);
        }
        return evaluateSingleToken(trimmed, trimmed, context);
    }

    private static boolean isQuoted(String value) {
        return value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")));
    }

    private static List<String> splitTopLevelCommas(String argsStr) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                parts.add(argsStr.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(argsStr.substring(start).trim());
        return parts;
    }

    private String evaluateNumericFunction(String funcName, String argsStr, String originalExpression) {
        Function<int[], String> function = PARAMETERIZED_FUNCTIONS.get(funcName);
        if (function == null) {
            return originalExpression;
        }
        try {
            return function.apply(parseIntArgs(argsStr));
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
