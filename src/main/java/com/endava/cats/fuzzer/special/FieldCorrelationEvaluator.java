package com.endava.cats.fuzzer.special;

import com.endava.cats.dsl.DynamicValueResolver;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evaluates request-side cross-field correlation rules (`correlate:`) and response-side
 * boolean correlation assertions (`expect.that:`).
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
        JsonElement parsedRoot = JsonUtils.parseAsJsonElement(jsonPayload);
        if (parsedRoot != null && parsedRoot.isJsonArray()) {
            JsonArray arr = parsedRoot.getAsJsonArray();
            JsonArray updatedArr = new JsonArray(arr.size());
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    String updatedEl = applyRequestCorrelations(contractPath, el.toString(), rules, variables, protectedFields);
                    updatedArr.add(JsonParser.parseString(updatedEl));
                } else {
                    updatedArr.add(el);
                }
            }
            return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(updatedArr);
        }
        String updated = jsonPayload;
        for (FieldCorrelationRule rule : rules) {
            if (!matchesPathPattern(contractPath, rule.paths())) {
                continue;
            }
            for (FieldCorrelationRule expandedRule : expandWildcardArrayRule(updated, rule)) {
                if (evaluateBooleanExpression(expandedRule.when(), updated, null, null, variables)) {
                    updated = applyRuleMutations(updated, expandedRule, variables, protectedFields);
                }
            }
        }
        return updated;
    }

    private static List<FieldCorrelationRule> expandWildcardArrayRule(String payload, FieldCorrelationRule rule) {
        String wildcardPrefix = findWildcardArrayPrefix(rule);
        if (wildcardPrefix == null) {
            return List.of(rule);
        }
        Object arrayObj = JsonUtils.getVariableFromJson(payload, normalizeFieldPath(wildcardPrefix));
        if (!(arrayObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<FieldCorrelationRule> expanded = new ArrayList<>(list.size());
        String token = wildcardPrefix + "[*]";
        for (int i = 0; i < list.size(); i++) {
            String indexed = wildcardPrefix + "[" + i + "]";
            String when = rule.when() == null ? null : rule.when().replace(token, indexed);
            Map<String, Object> set = new LinkedHashMap<>();
            Optional.ofNullable(rule.set()).orElse(Map.of())
                    .forEach((k, v) -> set.put(k.replace(token, indexed), v));
            List<String> omit = Optional.ofNullable(rule.omit()).orElse(List.of()).stream()
                    .map(o -> o.replace(token, indexed))
                    .toList();
            expanded.add(new FieldCorrelationRule(rule.paths(), when, set, omit));
        }
        return expanded;
    }

    private static String findWildcardArrayPrefix(FieldCorrelationRule rule) {
        if (rule.when() != null && rule.when().contains("[*]")) {
            return extractPrefixBeforeWildcard(rule.when());
        }
        for (String omit : Optional.ofNullable(rule.omit()).orElse(List.of())) {
            if (omit.contains("[*]")) {
                return extractPrefixBeforeWildcard(omit);
            }
        }
        for (String key : Optional.ofNullable(rule.set()).orElse(Map.of()).keySet()) {
            if (key.contains("[*]")) {
                return extractPrefixBeforeWildcard(key);
            }
        }
        return null;
    }

    private static String extractPrefixBeforeWildcard(String text) {
        int idx = text.indexOf("[*]");
        if (idx <= 0) {
            return null;
        }
        int start = idx - 1;
        while (start >= 0 && (Character.isLetterOrDigit(text.charAt(start)) || text.charAt(start) == '.' || text.charAt(start) == '_' || text.charAt(start) == '#')) {
            start--;
        }
        return text.substring(start + 1, idx);
    }

    private static String applyRuleMutations(String payload, FieldCorrelationRule rule,
                                             Map<String, String> variables, Set<String> protectedFields) {
        String current = payload;
        for (String omitField : Optional.ofNullable(rule.omit()).orElse(List.of())) {
            String normalizedPath = normalizeFieldPath(omitField);
            if (!isFieldProtected(normalizedPath, protectedFields)) {
                current = deleteFieldAtPath(current, normalizedPath);
            }
        }
        for (Map.Entry<String, Object> entry : Optional.ofNullable(rule.set()).orElse(Map.of()).entrySet()) {
            String normalizedPath = normalizeFieldPath(entry.getKey());
            Object resolvedVal = resolveRuleValue(entry.getValue(), variables);
            if (!isFieldProtected(normalizedPath, protectedFields)) {
                current = setOrInsertField(current, normalizedPath, resolvedVal);
            } else if (JsonUtils.isFieldInJson(current, normalizedPath)) {
                Object currentVal = JsonUtils.getVariableFromJson(current, normalizedPath);
                FuzzingStrategy strategy = FuzzingStrategy.fromValue(currentVal);
                if (!"REPLACE".equalsIgnoreCase(strategy.name())) {
                    Object mergedVal = strategy.process(resolvedVal);
                    current = setOrInsertField(current, normalizedPath, mergedVal);
                }
            }
        }
        return current;
    }

    private static boolean isFieldProtected(String normalizedPath, Set<String> protectedFields) {
        if (protectedFields == null || protectedFields.isEmpty()) {
            return false;
        }
        String unindexed = normalizedPath.replaceAll("\\[\\d+]", "");
        String leaf = unindexed.contains("#")
                ? unindexed.substring(unindexed.lastIndexOf('#') + 1)
                : unindexed;
        return protectedFields.contains(normalizedPath)
                || protectedFields.contains(unindexed)
                || (!unindexed.contains("#") && protectedFields.contains(leaf));
    }

    private static String deleteFieldAtPath(String jsonPayload, String fieldPath) {
        if (!fieldPath.contains("[")) {
            return JsonUtils.isFieldInJson(jsonPayload, fieldPath)
                    ? JsonUtils.deleteNode(jsonPayload, fieldPath)
                    : jsonPayload;
        }
        JsonObject root = JsonParser.parseString(StringUtils.defaultIfBlank(jsonPayload, "{}")).getAsJsonObject();
        JsonObject parent = navigateToParentObject(root, fieldPath.split("#", -1));
        if (parent == null) {
            return jsonPayload;
        }
        String[] parts = fieldPath.split("#", -1);
        parent.remove(parts[parts.length - 1]);
        return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(root);
    }

    public static String setOrInsertField(String jsonPayload, String fieldPath, Object value) {
        return setOrInsertField(jsonPayload, fieldPath, value, false);
    }

    public static String setOrInsertField(String jsonPayload, String fieldPath, Object value, boolean createMissingParents) {
        if (!fieldPath.contains("[") && JsonUtils.isFieldInJson(jsonPayload, fieldPath)) {
            return CatsUtil.justReplaceField(jsonPayload, fieldPath, value).json();
        }
        JsonObject root = JsonParser.parseString(StringUtils.defaultIfBlank(jsonPayload, "{}")).getAsJsonObject();
        String[] parts = fieldPath.split("#", -1);
        JsonObject parent = navigateToParentObject(root, parts, createMissingParents);
        if (parent == null) {
            return jsonPayload;
        }
        parent.add(parts[parts.length - 1], JsonUtils.GSON_NO_PRETTY_PRINTING.toJsonTree(value));
        return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(root);
    }

    private static JsonObject navigateToParentObject(JsonObject root, String[] parts) {
        return navigateToParentObject(root, parts, false);
    }

    private static JsonObject navigateToParentObject(JsonObject root, String[] parts, boolean createMissingParents) {
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1 && current != null; i++) {
            String segment = parts[i];
            int bracketOpen = segment.indexOf('[');
            current = (bracketOpen > 0 && segment.endsWith("]"))
                    ? navigateArraySegment(current, segment, bracketOpen)
                    : navigateObjectSegment(current, segment, createMissingParents);
        }
        return current;
    }

    private static JsonObject navigateArraySegment(JsonObject current, String segment, int bracketOpen) {
        String arrayName = segment.substring(0, bracketOpen);
        int index = Integer.parseInt(segment.substring(bracketOpen + 1, segment.length() - 1));
        if (!current.has(arrayName) || !current.get(arrayName).isJsonArray()) {
            return null;
        }
        JsonArray arr = current.getAsJsonArray(arrayName);
        if (index < 0 || index >= arr.size() || !arr.get(index).isJsonObject()) {
            return null;
        }
        return arr.get(index).getAsJsonObject();
    }

    private static JsonObject navigateObjectSegment(JsonObject current, String segment, boolean createMissingParents) {
        if (!current.has(segment) || !current.get(segment).isJsonObject()) {
            if (!createMissingParents) {
                return null;
            }
            current.add(segment, new JsonObject());
        }
        return current.getAsJsonObject(segment);
    }

    private static Object resolveRuleValue(Object rawValue, Map<String, String> variables) {
        if (rawValue instanceof String strVal) {
            return DynamicValueResolver.resolve(strVal, variables);
        }
        return rawValue;
    }

    /**
     * Evaluates all `expect.that` correlation expressions against the response and request,
     * returning the first failed expression with resolved field values, or empty if all pass.
     */
    public static Optional<String> evaluateThatExpectations(List<String> thatExpressions,
                                                            String requestJson,
                                                            CatsResponse response,
                                                            Map<String, String> variables) {
        if (thatExpressions == null || thatExpressions.isEmpty()) {
            return Optional.empty();
        }
        for (String expr : thatExpressions) {
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
        if (atom.startsWith("T(")) {
            Map<String, String> ctx = new LinkedHashMap<>(Optional.ofNullable(variables).orElse(Map.of()));
            if (requestJson != null) {
                ctx.put("request", requestJson);
            }
            if (responseJson != null) {
                ctx.put("response", responseJson);
            }
            String evaluated = DynamicValueResolver.resolve(atom, ctx);
            return Boolean.parseBoolean(evaluated);
        }
        Object truthy = resolveOperand(atom, requestJson, responseJson, response, variables);
        return Boolean.parseBoolean(String.valueOf(truthy));
    }

    private static boolean compareOperands(Object left, Object right, String op) {
        if (left == null || right == null || JsonUtils.isNotSet(String.valueOf(left)) || JsonUtils.isNotSet(String.valueOf(right))) {
            return "!=".equals(op) && !Objects.equals(String.valueOf(left), String.valueOf(right));
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
            return left.compareToIgnoreCase(right);
        }
    }

    private static Object resolveOperand(String token,
                                         String requestJson,
                                         String responseJson,
                                         CatsResponse response,
                                         Map<String, String> variables) {
        String trimmed = token.trim();
        Optional<Object> literalOrVar = resolveLiteralOrVariable(trimmed, variables);
        if (literalOrVar.isPresent()) {
            return literalOrVar.get();
        }
        Optional<Object> metadata = resolveResponseMetadata(trimmed, requestJson, responseJson, response);
        return metadata.orElseGet(() -> resolveJsonFieldOperand(trimmed, requestJson, responseJson, variables));
    }

    private static Optional<Object> resolveLiteralOrVariable(String trimmed, Map<String, String> variables) {
        if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return Optional.ofNullable(DynamicValueResolver.resolve(trimmed.substring(1, trimmed.length() - 1), variables));
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Optional.of(Boolean.valueOf(trimmed));
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return Optional.of(new BigDecimal(trimmed));
        }
        if (trimmed.startsWith("${") && trimmed.endsWith("}") && variables != null) {
            String varKey = trimmed.substring(2, trimmed.length() - 1);
            Optional<Object> stepRef = resolveStepReference(varKey, variables);
            return Optional.of(stepRef.orElseGet(() -> variables.getOrDefault(varKey, JsonUtils.NOT_SET)));
        }
        return Optional.empty();
    }

    static Optional<Object> resolveStepReference(String rawToken, Map<String, String> variables) {
        if (variables == null || StringUtils.isBlank(rawToken)) {
            return Optional.empty();
        }
        String clean = rawToken.startsWith("$") ? rawToken.substring(1) : rawToken;
        if (variables.containsKey(clean)) {
            return Optional.ofNullable(variables.get(clean));
        }
        if (clean.startsWith("prev.request.") || clean.startsWith("prev.response.")) {
            boolean isReq = clean.startsWith("prev.request.");
            String prefix = isReq ? "prev.request" : "prev.response";
            String fieldPath = clean.substring(prefix.length() + 1);
            return Optional.of(lookupJsonPath(variables.get(prefix), fieldPath));
        }
        if (!clean.startsWith("steps.")) {
            return Optional.empty();
        }
        String afterSteps = clean.substring("steps.".length());
        int reqIdx = afterSteps.indexOf(".request.");
        if (reqIdx > 0) {
            String stepKey = afterSteps.substring(0, reqIdx);
            String fieldPath = afterSteps.substring(reqIdx + ".request.".length());
            return Optional.of(lookupJsonPath(variables.get("steps." + stepKey + ".request"), fieldPath));
        }
        int respIdx = afterSteps.indexOf(".response.");
        if (respIdx > 0) {
            String stepKey = afterSteps.substring(0, respIdx);
            String fieldPath = afterSteps.substring(respIdx + ".response.".length());
            return Optional.of(lookupJsonPath(variables.get("steps." + stepKey + ".response"), fieldPath));
        }
        return Optional.empty();
    }

    private static Optional<Object> resolveResponseMetadata(String trimmed, String requestJson,
                                                            String responseJson, CatsResponse response) {
        if (("responseTimeMs".equalsIgnoreCase(trimmed) || "$responseTimeMs".equalsIgnoreCase(trimmed) || "time".equalsIgnoreCase(trimmed))
                && response != null) {
            return Optional.of(response.getResponseTimeInMs());
        }
        if (("$status".equalsIgnoreCase(trimmed) || "status".equalsIgnoreCase(trimmed)) && response != null) {
            return Optional.of(response.getResponseCode());
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("size(") && trimmed.endsWith(")")) {
            String innerField = trimmed.substring(5, trimmed.length() - 1).trim();
            return Optional.of(evaluateCollectionSize(innerField, responseJson != null ? responseJson : requestJson));
        }
        return Optional.empty();
    }

    private static Object resolveJsonFieldOperand(String trimmed, String requestJson,
                                                  String responseJson, Map<String, String> variables) {
        Optional<Object> stepRef = resolveStepReference(trimmed, variables);
        if (stepRef.isPresent()) {
            return stepRef.get();
        }
        if (trimmed.startsWith("$request.") || trimmed.startsWith("request.")) {
            String field = trimmed.substring(trimmed.indexOf('.') + 1);
            return lookupJsonPath(requestJson, field);
        }
        if (trimmed.startsWith("$response.") || trimmed.startsWith("response.")) {
            String field = trimmed.substring(trimmed.indexOf('.') + 1);
            return lookupJsonPath(responseJson, field);
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
        if ("$".equals(fieldPath) || "response".equalsIgnoreCase(fieldPath) || "$response".equalsIgnoreCase(fieldPath)) {
            JsonElement root = JsonUtils.parseAsJsonElement(json);
            if (root.isJsonObject()) {
                return root.getAsJsonObject().size();
            }
            if (root.isJsonArray()) {
                return root.getAsJsonArray().size();
            }
            return 0;
        }
        Object value = JsonUtils.getVariableFromJson(json, normalizeFieldPath(fieldPath));
        if (value instanceof List<?> list) {
            return list.size();
        }
        return JsonUtils.isNotSet(String.valueOf(value)) ? 0 : 1;
    }

    public static Object lookupJsonPath(String json, String path) {
        if (StringUtils.isBlank(json)) {
            return JsonUtils.NOT_SET;
        }
        String normalized = normalizeFieldPath(path);
        Object value = JsonUtils.getVariableFromJson(json, normalized);
        if (JsonUtils.isNotSet(String.valueOf(value)) && json.trim().startsWith("[") && JsonUtils.isValidJson(json)) {
            JsonElement root = JsonUtils.parseAsJsonElement(json);
            if (root.isJsonArray() && !root.getAsJsonArray().isEmpty() && root.getAsJsonArray().get(0).isJsonObject()) {
                return JsonUtils.getVariableFromJson(root.getAsJsonArray().get(0).toString(), normalized);
            }
        }
        return value;
    }

    public static String normalizeFieldPath(String fieldPath) {
        if (fieldPath == null) {
            return "";
        }
        String trimmed = fieldPath.trim();
        if (trimmed.startsWith("$.") || trimmed.startsWith("$#")) {
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
            } else if (!inSingle && !inDouble && isOperatorMatchAt(text, op, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isOperatorMatchAt(String text, String op, int i) {
        if (!text.regionMatches(true, i, op, 0, op.length())) {
            return false;
        }
        if (!"=".equals(op)) {
            return true;
        }
        boolean partOfCompoundPrev = i > 0 && isCompoundOperatorPrefix(text.charAt(i - 1));
        boolean partOfCompoundNext = i + 1 < text.length() && text.charAt(i + 1) == '=';
        return !partOfCompoundPrev && !partOfCompoundNext;
    }

    private static boolean isCompoundOperatorPrefix(char prev) {
        return prev == '!' || prev == '>' || prev == '<' || prev == '=';
    }
}
