package com.endava.cats.fuzzer.special;

import com.endava.cats.dsl.DynamicValueResolver;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evaluates request-side cross-field correlation rules (`correlate:`) and response-side
 * boolean correlation assertions (`expect.where:`).
 */
public final class FieldCorrelationEvaluator {
    private static final List<String> OPERATORS = List.of(">=", "<=", "!=", "==", "=", ">", "<", " contains ", " matches ");

    private FieldCorrelationEvaluator() {
        // utility class
    }

    /**
     * Applies all matching correlation rules (`when` -> `set` / `omit`) to the given JSON request payload.
     */
    public static String applyRequestCorrelations(String contractPath, String jsonPayload,
                                                  List<FieldCorrelationRule> rules,
                                                  Map<String, String> variables) {
        return applyRequestCorrelations(contractPath, jsonPayload, rules, variables, Set.of());
    }

    /**
     * Applies all matching correlation rules (`when` -> `set` / `omit`) to the given JSON request payload,
     * skipping any target field present in {@code protectedFields}.
     */
    public static String applyRequestCorrelations(String contractPath, String jsonPayload,
                                                  List<FieldCorrelationRule> rules,
                                                  Map<String, String> variables,
                                                  Set<String> protectedFields) {
        if (StringUtils.isBlank(jsonPayload) || !JsonUtils.isValidJson(jsonPayload) || rules == null || rules.isEmpty()) {
            return jsonPayload;
        }
        String updated = jsonPayload;
        for (FieldCorrelationRule rule : rules) {
            if (matchesPathPattern(contractPath, rule.paths())
                    && evaluateBooleanExpression(rule.when(), updated, null, null, variables)) {
                updated = applyRuleMutations(updated, rule, variables, protectedFields);
            }
        }
        return updated;
    }

    private static String applyRuleMutations(String payload, FieldCorrelationRule rule,
                                             Map<String, String> variables, Set<String> protectedFields) {
        String current = payload;
        for (String omitField : Optional.ofNullable(rule.omit()).orElse(List.of())) {
            String normalizedPath = normalizeFieldPath(omitField);
            if (!isFieldProtected(normalizedPath, protectedFields) && JsonUtils.isFieldInJson(current, normalizedPath)) {
                current = JsonUtils.deleteNode(current, normalizedPath);
            }
        }
        for (Map.Entry<String, Object> entry : Optional.ofNullable(rule.set()).orElse(Map.of()).entrySet()) {
            String normalizedPath = normalizeFieldPath(entry.getKey());
            if (!isFieldProtected(normalizedPath, protectedFields)) {
                Object resolvedVal = resolveRuleValue(entry.getValue(), current, variables);
                current = setOrInsertField(current, normalizedPath, resolvedVal);
            }
        }
        return current;
    }

    private static boolean isFieldProtected(String normalizedPath, Set<String> protectedFields) {
        if (protectedFields == null || protectedFields.isEmpty()) {
            return false;
        }
        String leaf = normalizedPath.contains("#")
                ? normalizedPath.substring(normalizedPath.lastIndexOf('#') + 1)
                : normalizedPath;
        return protectedFields.contains(normalizedPath) || protectedFields.contains(leaf);
    }

    public static String setOrInsertField(String jsonPayload, String fieldPath, Object value) {
        if (JsonUtils.isFieldInJson(jsonPayload, fieldPath)) {
            return CatsUtil.justReplaceField(jsonPayload, fieldPath, value).json();
        }
        JsonObject root = JsonParser.parseString(StringUtils.defaultIfBlank(jsonPayload, "{}")).getAsJsonObject();
        String[] parts = fieldPath.split("#", -1);
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String segment = parts[i];
            if (!current.has(segment) || !current.get(segment).isJsonObject()) {
                return jsonPayload;
            }
            current = current.getAsJsonObject(segment);
        }
        current.add(parts[parts.length - 1], JsonUtils.GSON_NO_PRETTY_PRINTING.toJsonTree(value));
        return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(root);
    }

    private static Object resolveRuleValue(Object rawValue, String requestJson, Map<String, String> variables) {
        if (rawValue instanceof String strVal) {
            return DynamicValueResolver.resolve(strVal, variables);
        }
        return rawValue;
    }

    /**
     * Evaluates all `expect.where` correlation expressions against the response and request,
     * returning the first failed expression with resolved field values, or empty if all pass.
     */
    public static Optional<String> evaluateWhereExpectations(List<String> whereExpressions,
                                                             String requestJson,
                                                             CatsResponse response,
                                                             Map<String, String> variables) {
        if (whereExpressions == null || whereExpressions.isEmpty()) {
            return Optional.empty();
        }
        for (String expr : whereExpressions) {
            if (StringUtils.isNotBlank(expr)
                    && !evaluateBooleanExpression(expr, requestJson, response.getBody(), response, variables)) {
                return Optional.of("Correlation condition failed: [" + expr + "]");
            }
        }
        return Optional.empty();
    }

    public static boolean evaluateBooleanExpression(String expression,
                                                    String requestJson,
                                                    String responseJson,
                                                    CatsResponse response,
                                                    Map<String, String> variables) {
        if (StringUtils.isBlank(expression)) {
            return true;
        }
        List<String> orClauses = splitLogicalTopLevel(expression, '|');
        for (String orClause : orClauses) {
            if (evaluateAndClause(orClause, requestJson, responseJson, response, variables)) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateAndClause(String andClause,
                                             String requestJson,
                                             String responseJson,
                                             CatsResponse response,
                                             Map<String, String> variables) {
        List<String> atoms = splitLogicalTopLevel(andClause, '&');
        for (String atom : atoms) {
            if (!evaluateAtomicPredicate(atom.trim(), requestJson, responseJson, response, variables)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateAtomicPredicate(String atom,
                                                   String requestJson,
                                                   String responseJson,
                                                   CatsResponse response,
                                                   Map<String, String> variables) {
        if (atom.isEmpty()) {
            return true;
        }
        if (atom.endsWith(" is null") || atom.endsWith(" == null") || atom.endsWith(" = null")) {
            String lhsExpr = atom.replaceFirst("(?i)\\s+(is|==|=)\\s+null$", "").trim();
            Object val = resolveOperand(lhsExpr, requestJson, responseJson, response, variables);
            return val == null || JsonUtils.isNotSet(String.valueOf(val)) || "null".equalsIgnoreCase(String.valueOf(val));
        }
        if (atom.endsWith(" is not null") || atom.endsWith(" != null") || atom.endsWith(" notNull")) {
            String lhsExpr = atom.replaceFirst("(?i)\\s+(is\\s+not\\s+null|!=\\s*null|notNull)$", "").trim();
            Object val = resolveOperand(lhsExpr, requestJson, responseJson, response, variables);
            return val != null && !JsonUtils.isNotSet(String.valueOf(val)) && !"null".equalsIgnoreCase(String.valueOf(val));
        }
        for (String op : OPERATORS) {
            int opIndex = findOperatorOutsideQuotes(atom, op);
            if (opIndex > 0) {
                String leftRaw = atom.substring(0, opIndex).trim();
                String rightRaw = atom.substring(opIndex + op.length()).trim();
                Object leftVal = resolveOperand(leftRaw, requestJson, responseJson, response, variables);
                Object rightVal = resolveOperand(rightRaw, requestJson, responseJson, response, variables);
                return compareOperands(leftVal, rightVal, op.trim());
            }
        }
        Object truthy = resolveOperand(atom, requestJson, responseJson, response, variables);
        return Boolean.parseBoolean(String.valueOf(truthy));
    }

    private static boolean compareOperands(Object left, Object right, String op) {
        if (left == null || right == null || JsonUtils.isNotSet(String.valueOf(left)) || JsonUtils.isNotSet(String.valueOf(right))) {
            return ("!=".equals(op)) && !Objects.equals(String.valueOf(left), String.valueOf(right));
        }
        String leftStr = String.valueOf(left).trim();
        String rightStr = String.valueOf(right).trim();
        if ("contains".equalsIgnoreCase(op)) {
            return leftStr.contains(rightStr);
        }
        if ("matches".equalsIgnoreCase(op)) {
            return Pattern.compile(rightStr).matcher(leftStr).matches();
        }
        int cmp = compareValues(leftStr, rightStr);
        return switch (op) {
            case "==", "=" -> cmp == 0;
            case "!=" -> cmp != 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    private static int compareValues(String left, String right) {
        try {
            return new BigDecimal(left).compareTo(new BigDecimal(right));
        } catch (NumberFormatException _) {
            return left.compareTo(right);
        }
    }

    private static Object resolveOperand(String token,
                                         String requestJson,
                                         String responseJson,
                                         CatsResponse response,
                                         Map<String, String> variables) {
        String trimmed = token.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return DynamicValueResolver.resolve(trimmed.substring(1, trimmed.length() - 1), variables);
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.valueOf(trimmed);
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return new BigDecimal(trimmed);
        }
        if (trimmed.startsWith("${") && trimmed.endsWith("}") && variables != null) {
            String varKey = trimmed.substring(2, trimmed.length() - 1);
            return variables.getOrDefault(varKey, JsonUtils.NOT_SET);
        }
        if (("responseTimeMs".equalsIgnoreCase(trimmed) || "$responseTimeMs".equalsIgnoreCase(trimmed) || "time".equalsIgnoreCase(trimmed))
                && response != null) {
            return response.getResponseTimeInMs();
        }
        if (("$status".equalsIgnoreCase(trimmed) || "status".equalsIgnoreCase(trimmed)) && response != null) {
            return response.getResponseCode();
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("size(") && trimmed.endsWith(")")) {
            String innerField = trimmed.substring(5, trimmed.length() - 1).trim();
            return evaluateCollectionSize(innerField, responseJson != null ? responseJson : requestJson);
        }
        if (trimmed.startsWith("$request.")) {
            return lookupJsonPath(requestJson, trimmed.substring("$request.".length()));
        }
        if (trimmed.startsWith("$response.")) {
            return lookupJsonPath(responseJson, trimmed.substring("$response.".length()));
        }
        String primaryJson = responseJson != null ? responseJson : requestJson;
        Object fromPrimary = lookupJsonPath(primaryJson, trimmed);
        if (!JsonUtils.isNotSet(String.valueOf(fromPrimary))) {
            return fromPrimary;
        }
        if (responseJson != null && requestJson != null) {
            Object fromRequest = lookupJsonPath(requestJson, trimmed);
            if (!JsonUtils.isNotSet(String.valueOf(fromRequest))) {
                return fromRequest;
            }
        }
        if (variables != null && variables.containsKey(trimmed)) {
            return variables.get(trimmed);
        }
        return JsonUtils.NOT_SET;
    }

    private static int evaluateCollectionSize(String fieldPath, String json) {
        if (StringUtils.isBlank(json)) {
            return 0;
        }
        Object value = JsonUtils.getVariableFromJson(json, normalizeFieldPath(fieldPath));
        if (value instanceof List<?> list) {
            return list.size();
        }
        return JsonUtils.isNotSet(String.valueOf(value)) ? 0 : 1;
    }

    private static Object lookupJsonPath(String json, String path) {
        if (StringUtils.isBlank(json)) {
            return JsonUtils.NOT_SET;
        }
        return JsonUtils.getVariableFromJson(json, normalizeFieldPath(path));
    }

    public static String normalizeFieldPath(String fieldPath) {
        if (fieldPath == null) {
            return "";
        }
        String trimmed = fieldPath.trim();
        if (trimmed.startsWith("$.")) {
            trimmed = trimmed.substring(2);
        }
        return trimmed.replace('.', '#');
    }

    private static boolean matchesPathPattern(String contractPath, List<String> patterns) {
        if (patterns == null || patterns.isEmpty() || contractPath == null) {
            return true;
        }
        for (String pattern : patterns) {
            if ("*".equals(pattern) || contractPath.equalsIgnoreCase(pattern)) {
                return true;
            }
            if (pattern.endsWith("*") && contractPath.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitLogicalTopLevel(String input, char operatorChar) {
        List<String> parts = new ArrayList<>();
        int lastStart = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble && c == operatorChar) {
                parts.add(input.substring(lastStart, i));
                if (i + 1 < input.length() && input.charAt(i + 1) == operatorChar) {
                    i++;
                }
                lastStart = i + 1;
            }
            i++;
        }
        parts.add(input.substring(lastStart));
        return parts;
    }

    private static int findOperatorOutsideQuotes(String text, String op) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i <= text.length() - op.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble && text.regionMatches(true, i, op, 0, op.length())) {
                if ("=".equals(op) && i > 0) {
                    char prev = text.charAt(i - 1);
                    if (prev == '!' || prev == '>' || prev == '<' || prev == '=') {
                        continue;
                    }
                }
                if ("=".equals(op) && i + 1 < text.length() && text.charAt(i + 1) == '=') {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }
}
