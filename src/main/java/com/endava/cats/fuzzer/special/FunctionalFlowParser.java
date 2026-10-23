package com.endava.cats.fuzzer.special;

import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.BasePayloadMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowExpectation;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
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
 * Detects and parses sequential functional flow YAML documents (`steps:` or top-level step list).
 */
public final class FunctionalFlowParser {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Set<String> RESERVED_EXPECT_KEYS = Set.of(
            "status", "fields", "headers", "bodycontains", "bodynotcontains", "where", "matchesschema");

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
        if (stepNode.has("method") && stepNode.has("path")) {
            return true;
        }
        var fieldNames = stepNode.fieldNames();
        while (fieldNames.hasNext()) {
            if (HttpMethod.fromString(fieldNames.next()).isPresent()) {
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
            List<FieldCorrelationRule> correlate = parseCorrelations(root.get("correlate"));
            List<FunctionalFlowStep> steps = parseSteps(root.get("steps"));
            return new FunctionalFlowDocument(variables, correlate, steps);
        } catch (IOException e) {
            throw new CatsException("Unable to parse functional flow YAML document", e);
        }
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
        String name = firstNonBlankText(node, "name", "description");
        if (StringUtils.isBlank(name)) {
            name = "Step " + index + ": " + methodAndTarget.method() + " " + methodAndTarget.rawTarget();
        }
        Map<String, Object> path = parseObjectMap(node.get("path"));
        Map<String, Object> query = parseObjectMap(node.get("query"));
        Map<String, Object> headers = parseObjectMap(node.get("headers"));
        Object body = node.has("body") ? convertJsonNode(node.get("body")) : null;
        BasePayloadMode base = BasePayloadMode.fromString(firstNonBlankText(node, "base", "mode"));
        List<String> omit = parseStringList(node.get("omit"));
        VariantSpec variantSpec = parseVariant(node.get("variant"));
        List<FieldCorrelationRule> correlate = parseCorrelations(node.get("correlate"));
        Map<String, String> save = parseStringMap(node.has("save") ? node.get("save") : node.get("output"));
        FunctionalFlowExpectation expect = parseExpectation(node);
        return new FunctionalFlowStep(
                name, methodAndTarget.method(), methodAndTarget.rawTarget(),
                path, query, headers, body, base, omit,
                variantSpec.name(), variantSpec.match(), correlate, save, expect);
    }

    private record MethodAndTarget(HttpMethod method, String rawTarget) {
    }

    private record VariantSpec(String name, Map<String, String> match) {
    }

    private static MethodAndTarget extractMethodAndTarget(JsonNode node, int index) {
        if (node.has("method") && node.has("path") && node.get("path").isTextual()) {
            HttpMethod method = HttpMethod.fromString(node.get("method").asText())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid HTTP method in step " + index));
            return new MethodAndTarget(method, node.get("path").asText().trim());
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            var maybeMethod = HttpMethod.fromString(entry.getKey());
            if (maybeMethod.isPresent() && entry.getValue().isTextual()) {
                return new MethodAndTarget(maybeMethod.get(), entry.getValue().asText().trim());
            }
        }
        throw new IllegalArgumentException("Functional flow step " + index + " must specify an HTTP method and target path (e.g. 'POST: /items')");
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
        JsonNode expectNode = stepNode.has("expect") ? stepNode.get("expect") : stepNode.get("verify");
        List<String> topStatus = parseStatusCodes(stepNode.get("status"));
        if (expectNode == null || !expectNode.isObject()) {
            List<String> status = topStatus.isEmpty() ? List.of("2XX") : topStatus;
            return new FunctionalFlowExpectation(status, Map.of(), Map.of(), List.of(), List.of(), List.of(), true);
        }
        List<String> expectStatus = parseStatusCodes(expectNode.get("status"));
        List<String> finalStatus = !expectStatus.isEmpty() ? expectStatus : (!topStatus.isEmpty() ? topStatus : List.of("2XX"));
        Map<String, Object> fields = new LinkedHashMap<>(parseObjectMap(expectNode.get("fields")));
        expectNode.properties().forEach(entry -> {
            if (!RESERVED_EXPECT_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                fields.put(entry.getKey(), convertJsonNode(entry.getValue()));
            }
        });
        Map<String, String> headers = parseStringMap(expectNode.get("headers"));
        List<String> bodyContains = parseStringList(expectNode.get("bodyContains"));
        List<String> bodyNotContains = parseStringList(expectNode.get("bodyNotContains"));
        List<String> where = parseStringList(expectNode.get("where"));
        boolean matchesSchema = !expectNode.has("matchesSchema") || expectNode.get("matchesSchema").asBoolean(true);
        return new FunctionalFlowExpectation(
                finalStatus, Collections.unmodifiableMap(fields), headers,
                bodyContains, bodyNotContains, where, matchesSchema);
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
        for (String token : raw.split(",", -1)) {
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
            return List.of(node.asText().trim());
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
