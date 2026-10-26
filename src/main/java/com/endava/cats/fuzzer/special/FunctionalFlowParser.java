package com.endava.cats.fuzzer.special;

import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.BasePayloadMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowExpectation;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FuzzMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.StepFuzzConfig;
import com.endava.cats.http.HttpMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects and parses sequential functional and security flow YAML documents (`steps:` or top-level step list).
 */
public final class FunctionalFlowParser {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Set<String> RESERVED_EXPECT_KEYS = Set.of(
            "status", "fields", "headers",
            "bodycontains", "bodynotcontains", "that", "matchesschema",
            "maxresponsetimems");

    private FunctionalFlowParser() {
        // utility class
    }

    public static boolean isFunctionalFlowFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        try {
            return isFunctionalFlowYaml(Files.readString(file.toPath()));
        } catch (IOException _) {
            return false;
        }
    }

    public static boolean isSecurityFlowFile(File file) {
        if (!isFunctionalFlowFile(file)) {
            return false;
        }
        try {
            FunctionalFlowDocument doc = parse(file);
            return doc.steps().stream().anyMatch(s -> s.fuzz() != null && s.fuzz().isConfigured());
        } catch (IOException | RuntimeException _) {
            return false;
        }
    }

    public static boolean isFunctionalFlowYaml(String yamlContent) {
        if (StringUtils.isBlank(yamlContent)) {
            return false;
        }
        try {
            JsonNode root = YAML_MAPPER.readTree(yamlContent);
            if (root == null) {
                return false;
            }
            if (root.isObject() && root.has("steps") && root.get("steps").isArray()) {
                return true;
            }
            if (root.isArray() && !root.isEmpty() && root.get(0).isObject()) {
                return hasHttpMethodEntry(root.get(0));
            }
            return false;
        } catch (IOException _) {
            return false;
        }
    }

    private static boolean hasHttpMethodEntry(JsonNode stepNode) {
        if (stepNode.has("method") && (stepNode.has("path") || stepNode.has("paths"))) {
            return true;
        }
        var fieldNames = stepNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if ("ALL".equalsIgnoreCase(fieldName) || HttpMethod.fromString(fieldName).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static FunctionalFlowDocument parse(File file) throws IOException {
        return parseContent(Files.readString(file.toPath()));
    }

    public static FunctionalFlowDocument parseContent(String yamlContent) {
        try {
            JsonNode root = YAML_MAPPER.readTree(yamlContent);
            if (root == null) {
                return new FunctionalFlowDocument(Map.of(), List.of(), List.of());
            }
            if (root.isArray()) {
                return new FunctionalFlowDocument(Map.of(), List.of(), parseSteps(root));
            }
            Map<String, String> variables = parseStringMap(root.get("variables"));
            JsonNode corrNode = root.get(root.has("correlate") ? "correlate" : "correlations");
            List<FieldCorrelationRule> correlate = parseCorrelations(corrNode);
            List<FunctionalFlowStep> steps = parseSteps(root.get("steps"));
            return new FunctionalFlowDocument(variables, correlate, steps);
        } catch (IOException e) {
            throw new CatsException("Unable to parse functional flow YAML document", e);
        }
    }

    public static List<FieldCorrelationRule> parseCorrelationFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            return List.of();
        }
        JsonNode root = YAML_MAPPER.readTree(Files.readString(file.toPath()));
        if (root == null) {
            return List.of();
        }
        if (root.isArray()) {
            return parseCorrelations(root);
        }
        if (root.isObject()) {
            JsonNode corrNode = root.get(root.has("correlate") ? "correlate" : "correlations");
            return parseCorrelations(corrNode);
        }
        return List.of();
    }

    private static List<FunctionalFlowStep> parseSteps(JsonNode stepsNode) {
        if (stepsNode == null || !stepsNode.isArray()) {
            return List.of();
        }
        List<FunctionalFlowStep> steps = new ArrayList<>();
        int index = 1;
        for (JsonNode stepNode : stepsNode) {
            if (stepNode != null && stepNode.isObject()) {
                steps.add(parseSingleStep(stepNode, index++));
            }
        }
        return Collections.unmodifiableList(steps);
    }

    private static FunctionalFlowStep parseSingleStep(JsonNode node, int index) {
        MethodAndTarget methodAndTarget = extractMethodAndTarget(node, index);
        String explicitName = firstNonBlankText(node, "name", "id");
        String description = firstNonBlankText(node, "description");
        String name = resolveStepName(explicitName, description, index, methodAndTarget);
        Map<String, Object> path = parsePathMap(node.get("path"));
        Map<String, Object> query = parseObjectMap(node.get("query"));
        Map<String, Object> headers = parseObjectMap(node.get("headers"));
        Object body = node.has("body") ? convertJsonNode(node.get("body")) : null;
        BasePayloadMode base = BasePayloadMode.fromString(firstNonBlankText(node, "base"));
        List<String> omit = parseStringList(node.get("omit"));
        VariantSpec variantSpec = parseVariant(node.get("variant"));
        JsonNode corrNode = node.get(node.has("correlate") ? "correlate" : "correlations");
        List<FieldCorrelationRule> correlate = parseCorrelations(corrNode);
        Map<String, String> save = parseSaveMap(node.get("save"));
        FunctionalFlowExpectation expect = parseExpectation(node);
        StepFuzzConfig fuzz = parseFuzzConfig(node);
        return new FunctionalFlowStep(
                name, description, methodAndTarget.method(), methodAndTarget.allMethods(),
                methodAndTarget.rawTarget(), methodAndTarget.targetPaths(),
                path, query, headers, body, base, omit,
                variantSpec.name(), variantSpec.match(), correlate, save, expect, fuzz);
    }

    private static String resolveStepName(String explicitName, String description, int index, MethodAndTarget target) {
        if (StringUtils.isNotBlank(explicitName)) {
            return explicitName;
        }
        if (StringUtils.isNotBlank(description)) {
            return description;
        }
        String verb = target.allMethods() ? "ALL" : String.valueOf(target.method());
        return "Step " + index + ": " + verb + " " + target.rawTarget();
    }

    private static Map<String, Object> parsePathMap(JsonNode pathNode) {
        if (pathNode != null && pathNode.isObject()) {
            return parseObjectMap(pathNode);
        }
        return Map.of();
    }

    private static StepFuzzConfig parseFuzzConfig(JsonNode stepNode) {
        if (!stepNode.has("fuzz") || !stepNode.get("fuzz").isObject()) {
            return null;
        }
        JsonNode fuzzNode = stepNode.get("fuzz");
        List<String> fields = parseStringList(fuzzNode.get("fields"));
        List<String> fieldTypes = parseStringList(fuzzNode.get("fieldTypes"));
        List<String> payloads = parseStringList(fuzzNode.get("payloads"));
        List<String> payloadsFiles = parseStringList(fuzzNode.get("payloadsFile"));
        FuzzMode mode = FuzzMode.fromString(firstNonBlankText(fuzzNode, "mode"));
        return new StepFuzzConfig(fields, fieldTypes, payloads, payloadsFiles, mode);
    }

    private record MethodAndTarget(HttpMethod method, boolean allMethods, String rawTarget, List<String> targetPaths) {
    }

    private record VariantSpec(String name, Map<String, String> match) {
    }

    private static MethodAndTarget extractMethodAndTarget(JsonNode node, int index) {
        String explicitMethod = firstNonBlankText(node, "method");
        List<String> explicitPaths = extractExplicitTargetPaths(node);
        if (explicitMethod != null && !explicitPaths.isEmpty()) {
            return buildMethodAndTarget(explicitMethod, explicitPaths, index);
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = entry.getKey();
            if (("ALL".equalsIgnoreCase(key) || HttpMethod.fromString(key).isPresent()) && isPathValueNode(entry.getValue())) {
                List<String> paths = parseStringList(entry.getValue());
                return buildMethodAndTarget(key, paths, index);
            }
        }
        if (!explicitPaths.isEmpty() && node.has("fuzz")) {
            return new MethodAndTarget(null, false, explicitPaths.getFirst(), explicitPaths);
        }
        throw new IllegalArgumentException("Functional flow step " + index + " must specify an HTTP method and target path (e.g. 'POST: /items')");
    }

    private static boolean isPathValueNode(JsonNode valueNode) {
        return valueNode != null && (valueNode.isTextual() || valueNode.isArray());
    }

    private static List<String> extractExplicitTargetPaths(JsonNode node) {
        List<String> paths = new ArrayList<>();
        if (node.has("path") && isPathValueNode(node.get("path"))) {
            paths.addAll(parseStringList(node.get("path")));
        }
        if (node.has("paths") && isPathValueNode(node.get("paths"))) {
            paths.addAll(parseStringList(node.get("paths")));
        }
        return paths;
    }

    private static MethodAndTarget buildMethodAndTarget(String methodRaw, List<String> paths, int index) {
        if ("ALL".equalsIgnoreCase(methodRaw) || "*".equals(methodRaw)) {
            return new MethodAndTarget(null, true, paths.getFirst(), List.copyOf(paths));
        }
        HttpMethod method = HttpMethod.fromString(methodRaw)
                .orElseThrow(() -> new IllegalArgumentException("Invalid HTTP method in step " + index));
        return new MethodAndTarget(method, false, paths.getFirst(), List.copyOf(paths));
    }

    private static VariantSpec parseVariant(JsonNode variantNode) {
        if (variantNode == null || variantNode.isNull()) {
            return new VariantSpec(null, Map.of());
        }
        if (variantNode.isTextual()) {
            return new VariantSpec(variantNode.asText().trim(), Map.of());
        }
        if (variantNode.isObject()) {
            return new VariantSpec(null, parseStringMap(variantNode));
        }
        return new VariantSpec(null, Map.of());
    }

    private static FunctionalFlowExpectation parseExpectation(JsonNode stepNode) {
        JsonNode expectNode = stepNode.get("expect");
        List<String> topStatus = parseStatusCodes(stepNode.get("status"));
        if (expectNode == null || !expectNode.isObject()) {
            List<String> status = topStatus.isEmpty() ? List.of("2XX") : topStatus;
            return new FunctionalFlowExpectation(status, Map.of(), Map.of(), List.of(), List.of(), List.of(), true, null);
        }
        List<String> expectStatus = parseStatusCodes(expectNode.get("status"));
        List<String> fallbackStatus = topStatus.isEmpty() ? List.of("2XX") : topStatus;
        List<String> finalStatus = expectStatus.isEmpty() ? fallbackStatus : expectStatus;
        Map<String, Object> fields = new LinkedHashMap<>(parseObjectMap(expectNode.get("fields")));
        expectNode.properties().forEach(entry -> {
            if (!RESERVED_EXPECT_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                fields.put(entry.getKey(), convertJsonNode(entry.getValue()));
            }
        });
        Map<String, String> headers = parseStringMap(expectNode.get("headers"));
        List<String> bodyContains = parseStringList(expectNode.get("bodyContains"));
        List<String> bodyNotContains = parseStringList(expectNode.get("bodyNotContains"));
        List<String> that = parseStringList(expectNode.get("that"));
        boolean matchesSchema = !expectNode.has("matchesSchema") || expectNode.get("matchesSchema").asBoolean(true);
        JsonNode timeNode = expectNode.get("maxResponseTimeMs");
        Long maxTimeMs = (timeNode != null && timeNode.isNumber()) ? timeNode.longValue() : null;
        return new FunctionalFlowExpectation(
                finalStatus, Collections.unmodifiableMap(fields), headers,
                bodyContains, bodyNotContains, that, matchesSchema, maxTimeMs);
    }

    private static List<FieldCorrelationRule> parseCorrelations(JsonNode correlateNode) {
        if (correlateNode == null || !correlateNode.isArray()) {
            return List.of();
        }
        List<FieldCorrelationRule> rules = new ArrayList<>();
        for (JsonNode ruleNode : correlateNode) {
            if (ruleNode != null && ruleNode.isObject()) {
                List<String> paths = parseStringList(ruleNode.get("paths"));
                String when = firstNonBlankText(ruleNode, "when", "if");
                JsonNode thenNode = ruleNode.has("then") ? ruleNode.get("then") : ruleNode;
                Map<String, Object> set = parseObjectMap(thenNode.get("set"));
                List<String> omit = parseStringList(thenNode.get("omit"));
                rules.add(new FieldCorrelationRule(paths, when, set, omit));
            }
        }
        return Collections.unmodifiableList(rules);
    }

    private static List<String> parseStatusCodes(JsonNode statusNode) {
        if (statusNode == null || statusNode.isNull()) {
            return List.of();
        }
        if (statusNode.isArray()) {
            return parseStringList(statusNode);
        }
        String raw = statusNode.asText().replace("[", "").replace("]", "").trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (String token : raw.split("[,|]", -1)) {
            if (StringUtils.isNotBlank(token)) {
                codes.add(token.trim());
            }
        }
        return Collections.unmodifiableList(codes);
    }

    private static List<String> parseStringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            return parseStatusCodes(node);
        }
        if (!node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && !item.isNull()) {
                list.add(item.asText().trim());
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static Map<String, String> parseSaveMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (node.isObject()) {
            return parseStringMap(node);
        }
        Map<String, String> map = new LinkedHashMap<>();
        if (node.isTextual()) {
            for (String item : parseStatusCodes(node)) {
                addShorthandSaveEntry(map, item);
            }
            return Collections.unmodifiableMap(map);
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    addShorthandSaveEntry(map, item.asText());
                } else if (item != null && item.isObject()) {
                    map.putAll(parseStringMap(item));
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static void addShorthandSaveEntry(Map<String, String> map, String rawSelector) {
        String selector = rawSelector == null ? "" : rawSelector.trim();
        if (selector.isEmpty()) {
            return;
        }
        int lastSep = Math.max(selector.lastIndexOf('.'), selector.lastIndexOf('#'));
        String varName = lastSep >= 0 ? selector.substring(lastSep + 1).trim() : selector;
        if (!varName.isEmpty()) {
            map.put(varName, selector);
        }
    }

    private static Map<String, String> parseStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            if (entry.getValue() != null && !entry.getValue().isNull()) {
                map.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Object> parseObjectMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        node.properties().forEach(entry -> map.put(entry.getKey(), convertJsonNode(entry.getValue())));
        return Collections.unmodifiableMap(map);
    }

    private static Object convertJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        return YAML_MAPPER.convertValue(node, Object.class);
    }

    private static String firstNonBlankText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child != null && child.isTextual() && StringUtils.isNotBlank(child.asText())) {
                return child.asText().trim();
            }
        }
        return null;
    }
}
