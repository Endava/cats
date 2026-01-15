package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.util.CatsRandom;
import com.jayway.jsonpath.JsonPath;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import javax.naming.OperationNotSupportedException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drop-in replacement for SpringELParser that supports a safe subset of SpEL-like expressions
 * without pulling Spring Expression + Spring Integration.
 * <p>
 * Principles:
 * - Allow T(...) only for whitelisted classes (security boundary)
 * - Allow calling (almost) any public method on:
 * - values produced by T(...) (and chaining on returned objects)
 * - values resolved from context/JSON (String, numbers, booleans)
 * - Minimal "deny list" for risky methods that enable reflection escapes
 * <p>
 * Fail-soft: if anything fails, returns the original expression.
 */
public class MiniDslParser implements Parser {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(getClass());

    private static final Map<String, Class<?>> ALLOWED_TYPES = Map.ofEntries(
            // Dates
            Map.entry("java.time.LocalDate", java.time.LocalDate.class),
            Map.entry("java.time.LocalDateTime", java.time.LocalDateTime.class),
            Map.entry("java.time.OffsetDateTime", java.time.OffsetDateTime.class),
            Map.entry("java.time.ZonedDateTime", java.time.ZonedDateTime.class),
            Map.entry("java.time.Instant", java.time.Instant.class),
            Map.entry("java.time.Duration", java.time.Duration.class),
            Map.entry("java.time.Period", java.time.Period.class),

            // Numbers
            Map.entry("java.math.BigDecimal", BigDecimal.class),
            Map.entry("java.math.BigInteger", BigInteger.class),

            // Strings
            Map.entry("java.lang.String", String.class),
            Map.entry("org.apache.commons.lang3.StringUtils", StringUtils.class),
            Map.entry("org.apache.commons.lang3.RandomStringUtils", RandomStringUtils.class),

            // Randomness
            Map.entry("java.util.UUID", UUID.class),
            Map.entry("com.endava.cats.util.CatsRandom", CatsRandom.class)
    );

    /**
     * Deny specific methods to prevent reflection escapes.
     */
    private static final Set<String> DENY_METHODS = Set.of(
            "getClass", "wait", "notify", "notifyAll"
    );

    @Override
    public String parse(String expression, Map<String, String> context) {
        log.trace("Parsing {}", expression);
        try {
            Object result = eval(expression, context);
            return result == null ? expression : String.valueOf(result);
        } catch (Exception e) {
            log.trace("Something went wrong while parsing: {}", e.getMessage());
            return expression;
        }
    }

    private Object eval(String expr, Map<String, String> ctx) throws Exception {
        expr = expr == null ? "" : expr.trim();

        if (ctx.containsKey(expr)) {
            return ctx.get(expr);
        }

        if (isSinglePlaceholder(expr)) {
            String inner = expr.substring(2, expr.length() - 1).trim();
            String val = resolvePlaceholder(inner, ctx);
            return val != null ? val : inner;
        }

        expr = substituteEmbeddedPlaceholders(expr, ctx);
        expr = substituteLeadingTokenTemplates(expr, ctx);

        if (expr.startsWith("T(")) {
            return evalTypeChain(expr, ctx);
        }

        InstanceCall call = tryParseInstanceCall(expr);
        if (call != null) {
            Object current = eval(call.target, ctx);
            current = invokeAnyAllowed(current, call.method, parseArgs(call.argsInside, ctx));

            String remainder = call.remainder;
            while (remainder != null && remainder.startsWith(".")) {
                String next = remainder.substring(1).trim();
                InstanceCall nextCall = tryParseInstanceCall("x." + next);
                if (nextCall == null) {
                    throw new IllegalArgumentException("Invalid chain remainder: " + remainder);
                }
                current = invokeAnyAllowed(current, nextCall.method, parseArgs(nextCall.argsInside, ctx));
                remainder = nextCall.remainder;
            }

            return current;
        }

        if (isStringLiteral(expr)) {
            return unquote(expr);
        }
        if (expr.matches("-?\\d+")) {
            return Integer.parseInt(expr);
        }
        if ("true".equalsIgnoreCase(expr) || "false".equalsIgnoreCase(expr)) {
            return Boolean.parseBoolean(expr);
        }

        if (looksLikeIdentifierOrPath(expr)) {
            Object v = resolveValue(expr, ctx);
            if (v != null) return v;
            return expr;
        }

        if (ctx.containsKey(expr)) {
            return ctx.get(expr);
        }
        return expr;
    }

    private boolean isSinglePlaceholder(String expr) {
        return expr.startsWith("${") && expr.endsWith("}") && findMatchingBrace(expr, 0) == expr.length() - 1;
    }

    private String resolvePlaceholder(String key, Map<String, String> ctx) {
        if (ctx.containsKey(key)) {
            return ctx.get(key);
        }
        Object v = resolveValue(key, ctx);
        return v == null ? null : String.valueOf(v);
    }

    private String substituteEmbeddedPlaceholders(String expr, Map<String, String> ctx) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < expr.length()) {
            int start = expr.indexOf("${", i);
            if (start < 0) {
                out.append(expr.substring(i));
                break;
            }
            out.append(expr, i, start);
            int end = findMatchingBrace(expr, start);
            if (end < 0) {
                out.append(expr.substring(start));
                break;
            }
            String inner = expr.substring(start + 2, end).trim();
            String replacement = resolvePlaceholder(inner, ctx);
            out.append(replacement != null ? replacement : inner);
            i = end + 1;
        }
        return out.toString();
    }

    private int findMatchingBrace(String s, int startIdx) {
        int open = s.indexOf('{', startIdx);
        if (open < 0) return -1;

        int depth = 0;
        boolean[] quoteState = new boolean[2];
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            updateQuoteState(c, quoteState);

            if (quoteState[0] || quoteState[1]) {
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private String substituteLeadingTokenTemplates(String expr, Map<String, String> ctx) {
        int dash = expr.indexOf('-');
        if (dash <= 0) {
            return expr;
        }

        String prefix = expr.substring(0, dash);
        if (prefix.matches("[A-Za-z_][A-Za-z0-9_]*") && ctx.containsKey(prefix)) {
            return ctx.get(prefix) + expr.substring(dash);
        }
        return expr;
    }

    private Object resolveValue(String key, Map<String, String> ctx) {
        if (ctx.containsKey(key)) {
            return ctx.get(key);
        }

        String requestJson = firstNonNull(ctx.get(Parser.REQUEST), ctx.get("request"));
        String responseJson = firstNonNull(ctx.get(Parser.RESPONSE), ctx.get("response"));

        if (key.startsWith("request.")) {
            return readJsonPath(requestJson, "$." + key.substring("request.".length()));
        }
        if (key.startsWith("response.")) {
            return readJsonPath(responseJson, "$." + key.substring("response.".length()));
        }

        Object fromResp = readJsonPath(responseJson, "$." + key);
        if (fromResp != null) {
            return fromResp;
        }

        return readJsonPath(requestJson, "$." + key);
    }

    private String firstNonNull(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private Object readJsonPath(String json, String path) {
        if (json == null || json.isBlank()) return null;
        try {
            return JsonPath.read(json, path);
        } catch (Exception _) {
            return null;
        }
    }

    private record InstanceCall(String target, String method, String argsInside, String remainder) {
    }

    private InstanceCall tryParseInstanceCall(String expr) {
        int dot = findTopLevelDotBeforeMethodCall(expr);
        if (dot < 0) {
            return null;
        }

        String target = expr.substring(0, dot).trim();
        String afterDot = expr.substring(dot + 1).trim();

        String method = readIdentifier(afterDot);
        if (method.isEmpty()) {
            return null;
        }

        String rest = afterDot.substring(method.length()).trim();
        if (!rest.startsWith("(")) {
            return null;
        }

        int argsEnd = findMatchingParen(rest, 0);
        String argsInside = rest.substring(1, argsEnd).trim();
        String remainder = rest.substring(argsEnd + 1).trim();

        if (!remainder.isEmpty() && !remainder.startsWith(".")) {
            return null;
        }

        return new InstanceCall(target, method, argsInside, remainder);
    }

    private int findTopLevelDotBeforeMethodCall(String expr) {
        int depthParen = 0;
        boolean[] quoteState = new boolean[2];

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);

            updateQuoteState(c, quoteState);

            if (quoteState[0] || quoteState[1]) {
                continue;
            }

            if (c == '(') {
                depthParen++;
            } else if (c == ')') {
                depthParen--;
            }

            if (c == '.' && depthParen == 0) {
                int j = i + 1;
                while (j < expr.length() && Character.isWhitespace(expr.charAt(j))) {
                    j++;
                }

                if (j >= expr.length() || !(Character.isLetter(expr.charAt(j)) || expr.charAt(j) == '_')) {
                    continue;
                }

                int k = j;
                while (k < expr.length()) {
                    char cc = expr.charAt(k);
                    if (Character.isLetterOrDigit(cc) || cc == '_') {
                        k++;
                    } else {
                        break;
                    }
                }

                while (k < expr.length() && Character.isWhitespace(expr.charAt(k))) {
                    k++;
                }
                if (k < expr.length() && expr.charAt(k) == '(') {
                    return i;
                }
            }
        }
        return -1;
    }

    private Object evalTypeChain(String expr, Map<String, String> ctx) throws Exception {
        int open = expr.indexOf('(');
        int close = findMatchingParen(expr, open);
        String fqcn = expr.substring(open + 1, close).trim();

        Class<?> type = ALLOWED_TYPES.get(fqcn);
        if (type == null) {
            throw new OperationNotSupportedException("Type not allowed: " + fqcn);
        }

        String rest = expr.substring(close + 1).trim();
        if (!rest.startsWith(".")) {
            throw new IllegalArgumentException("Expected method chain after T(" + fqcn + ")");
        }

        Object current = type;

        while (rest.startsWith(".")) {
            rest = rest.substring(1).trim();
            String method = readIdentifier(rest);
            rest = rest.substring(method.length()).trim();

            if (!rest.startsWith("(")) {
                throw new IllegalArgumentException("Expected '(' after " + method);
            }

            int argsEnd = findMatchingParen(rest, 0);
            String argsInside = rest.substring(1, argsEnd).trim();
            rest = rest.substring(argsEnd + 1).trim();

            Object[] args = parseArgs(argsInside, ctx);

            if (current instanceof Class<?> cls) {
                current = invokeStaticAllowed(cls, method, args);
            } else {
                current = invokeAnyAllowed(current, method, args);
            }
        }

        return current;
    }

    private Object[] parseArgs(String argsInside, Map<String, String> ctx) throws Exception {
        if (argsInside.isEmpty()) {
            return new Object[0];
        }
        List<String> parts = splitTopLevel(argsInside);
        Object[] out = new Object[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            out[i] = eval(parts.get(i), ctx);
        }
        return out;
    }

    private Object invokeStaticAllowed(Class<?> cls, String method, Object[] args) throws Exception {
        if (DENY_METHODS.contains(method)) {
            throw new OperationNotSupportedException("Method not allowed: " + method);
        }
        Method m = findBestMethod(cls, method, true, args);
        return m.invoke(null, coerceArgs(m.getParameterTypes(), args));
    }

    private Object invokeAnyAllowed(Object target, String method, Object[] args) throws Exception {
        if (target == null) {
            return null;
        }
        if (DENY_METHODS.contains(method)) {
            throw new OperationNotSupportedException("Method not allowed: " + method);
        }

        if ("toString".equals(method) && args.length == 0) {
            return String.valueOf(target);
        }

        if (target instanceof String || target instanceof OffsetDateTime || target instanceof LocalDate) {
            Method m = findBestMethod(target.getClass(), method, false, args);
            return m.invoke(target, coerceArgs(m.getParameterTypes(), args));
        }

        throw new OperationNotSupportedException("Instance calls not allowed on: " + target.getClass().getName());
    }

    private Method findBestMethod(Class<?> cls, String name, boolean wantStatic, Object[] args) {
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            if (Modifier.isStatic(m.getModifiers()) != wantStatic) {
                continue;
            }
            if (m.getParameterCount() != args.length) {
                continue;
            }
            if (!areArgsCompatible(m.getParameterTypes(), args)) {
                continue;
            }
            return m;
        }
        throw new IllegalArgumentException("No matching method: " + cls.getName() + "." + name + "/" + args.length);
    }

    private boolean areArgsCompatible(Class<?>[] params, Object[] args) {
        for (int i = 0; i < params.length; i++) {
            Object a = args[i];
            if (a == null) {
                continue;
            }

            Class<?> p = wrap(params[i]);

            switch (a) {
                case Integer _ when p == Long.class -> {
                    continue;
                }
                case String _ when p == CharSequence.class -> {
                    continue;
                }
                case String s when p == Character.class && s.length() == 1 -> {
                    continue;
                }
                default -> {
                    //do nothing
                }
            }

            if (!p.isAssignableFrom(a.getClass())) {
                return false;
            }
        }
        return true;
    }

    private Object[] coerceArgs(Class<?>[] params, Object[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            Class<?> p = params[i];

            switch (a) {
                case Integer aInt when (p == long.class || p == Long.class) -> out[i] = aInt.longValue();
                case String s when (p == char.class || p == Character.class) && s.length() == 1 -> out[i] = s.charAt(0);
                case null, default -> out[i] = a;
            }
        }
        return out;
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        if (c == int.class) {
            return Integer.class;
        }
        if (c == long.class) {
            return Long.class;
        }
        if (c == boolean.class) {
            return Boolean.class;
        }
        if (c == char.class) {
            return Character.class;
        }
        return c;
    }

    private boolean looksLikeIdentifierOrPath(String s) {
        return s.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    }

    private boolean isStringLiteral(String s) {
        return (s.startsWith("'") && s.endsWith("'") && s.length() >= 2)
                || (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2);
    }

    private String unquote(String s) {
        return s.substring(1, s.length() - 1);
    }

    private String readIdentifier(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                i++;
            } else {
                break;
            }
        }
        return i == 0 ? "" : s.substring(0, i);
    }

    private void updateQuoteState(char c, boolean[] quoteState) {
        boolean inSingle = quoteState[0];
        boolean inDouble = quoteState[1];

        if (c == '\'' && !inDouble) {
            quoteState[0] = !inSingle;
        } else if (c == '"' && !inSingle) {
            quoteState[1] = !inDouble;
        }
    }

    private int findMatchingParen(String s, int openIndex) {
        int depth = 0;
        boolean[] quoteState = new boolean[2];
        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);

            updateQuoteState(c, quoteState);

            if (quoteState[0] || quoteState[1]) {
                continue;
            }

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new IllegalArgumentException("Unmatched parentheses: " + s);
    }

    private List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean[] quoteState = new boolean[2];

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            updateQuoteState(c, quoteState);

            if (!quoteState[0] && !quoteState[1]) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }

        String last = cur.toString().trim();
        if (!last.isEmpty()) {
            out.add(last);
        }
        return out;
    }
}