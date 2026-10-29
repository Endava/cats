package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.OpenApiUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Detects schema and parameter {@code default}/{@code example} values that violate the schema declaring them:
 * type, format, length, range, multipleOf, pattern, item count/uniqueness and nested property/item values.
 * <p>
 * Invalid defaults are reported as errors as they are materialized by servers and code generators.
 * Invalid examples are reported as warnings. Top-level enum membership is intentionally not checked here as it is
 * already covered by {@link ContradictorySchemaConstraintsLinter}.
 */
@Linter
@Singleton
public class SchemaValueConformanceLinter extends BaseLinter {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_VALUE_LENGTH = 60;
    private static final String STRING = "string";
    private static final String INTEGER = "integer";
    private static final String NUMBER = "number";
    private static final String ARRAY = "array";
    private static final String OBJECT = "object";

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new SchemaValueConformanceLinter instance.
     *
     * @param tcl the test case listener
     */
    public SchemaValueConformanceLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that default and example values conform to the type, format, length, range, pattern and item constraints of their own schema");
        testCaseListener.addExpectedResult(log, "Every default and example value must be valid against the schema declaring it");
        super.addDefaultsForPathAgnosticFuzzers();

        OpenAPI openAPI = data.getOpenApi();
        if (openAPI == null) {
            testCaseListener.skipTest(log, "OpenAPI specification not available");
            return;
        }

        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(openAPI);
        Set<String> invalidDefaults = new LinkedHashSet<>();
        Set<String> invalidExamples = new LinkedHashSet<>();

        SchemaWalker.walk(openAPI, (loc, schema) -> checkSchemaValues("Schema '%s'".formatted(loc.fqn()), schema, componentSchemas, invalidDefaults, invalidExamples));
        checkParameterExamples(openAPI, componentSchemas, invalidExamples);

        if (!invalidDefaults.isEmpty()) {
            List<String> all = new ArrayList<>(invalidDefaults);
            all.addAll(invalidExamples);
            testCaseListener.reportResultError(log, data, "Default values not conforming to their schema found", String.join("\n", all));
        } else if (!invalidExamples.isEmpty()) {
            testCaseListener.reportResultWarn(log, data, "Example values not conforming to their schema found", String.join("\n", invalidExamples));
        } else {
            testCaseListener.reportResultInfo(log, data, "All default and example values conform to their schemas");
        }
    }

    @SuppressWarnings("rawtypes")
    private static void checkSchemaValues(String location, Schema<?> schema, Map<String, Schema> componentSchemas,
                                          Set<String> invalidDefaults, Set<String> invalidExamples) {
        if (schema == null) {
            return;
        }
        collectViolations(location, "default", schema.getDefault(), schema, componentSchemas, invalidDefaults);
        collectViolations(location, "example", schema.getExample(), schema, componentSchemas, invalidExamples);
        if (schema.getExamples() != null) {
            schema.getExamples().forEach(example -> collectViolations(location, "example", example, schema, componentSchemas, invalidExamples));
        }
    }

    @SuppressWarnings("rawtypes")
    private static void checkParameterExamples(OpenAPI openAPI, Map<String, Schema> componentSchemas, Set<String> invalidExamples) {
        if (openAPI.getPaths() == null) {
            return;
        }
        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                for (Parameter parameter : OpenApiUtils.collectPathAndOperationParameters(pathItem, opEntry.getValue(), openAPI)) {
                    Schema<?> parameterSchema = CatsModelUtils.resolveSchema(parameter.getSchema(), componentSchemas);
                    if (parameterSchema == null || isSerializedArrayExample(parameter.getExample(), parameterSchema)) {
                        continue;
                    }
                    String location = "Parameter '%s' (in: %s) of %s %s".formatted(parameter.getName(), parameter.getIn(), opEntry.getKey(), pathEntry.getKey());
                    collectViolations(location, "example", parameter.getExample(), parameterSchema, componentSchemas, invalidExamples);
                }
            }
        }
    }

    /**
     * Array parameters are often documented using their serialized form, e.g. {@code example: "a,b"}.
     */
    private static boolean isSerializedArrayExample(Object example, Schema<?> parameterSchema) {
        return example instanceof String && CatsModelUtils.isArraySchema(parameterSchema);
    }

    @SuppressWarnings("rawtypes")
    private static void collectViolations(String location, String kind, Object value, Schema<?> schema,
                                          Map<String, Schema> componentSchemas, Set<String> target) {
        JsonNode node = toJsonNode(value);
        if (node == null) {
            return;
        }
        List<String> problems = new ArrayList<>();
        validate(node, schema, "", componentSchemas, 0, problems);
        problems.forEach(problem -> target.add("%s %s %s: %s".formatted(location, kind,
                StringUtils.abbreviate(node.toString(), MAX_VALUE_LENGTH), problem)));
    }

    /**
     * Values already converted by the OpenAPI parser into dates, UUIDs or bytes were validated during parsing.
     */
    private static JsonNode toJsonNode(Object value) {
        if (value == null || value instanceof Date || value instanceof TemporalAccessor || value instanceof UUID || value instanceof byte[]) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return Json.mapper().valueToTree(value);
    }

    @SuppressWarnings("rawtypes")
    private static void validate(JsonNode value, Schema<?> rawSchema, String path, Map<String, Schema> componentSchemas,
                                 int depth, List<String> problems) {
        Schema<?> schema = CatsModelUtils.resolveSchema(rawSchema, componentSchemas);
        if (value == null || value.isNull() || schema == null || schema.get$ref() != null
                || depth > MAX_DEPTH || CatsModelUtils.isComposedSchema(schema)) {
            return;
        }
        Set<String> types = declaredTypes(schema);
        JsonNode node = types.contains(OBJECT) || types.contains(ARRAY) ? parseEmbeddedJson(value) : value;
        String actualType = jsonType(node);
        if (types.isEmpty() || isCoercibleScalar(node, types)) {
            return;
        }
        if (!isTypeAccepted(actualType, types)) {
            problems.add("%sis %s but schema type is %s".formatted(at(path), actualType, types));
            return;
        }
        switch (actualType) {
            case STRING -> checkString(node.asText(), schema, path, problems);
            case INTEGER, NUMBER -> checkNumber(node.decimalValue(), schema, path, problems);
            case ARRAY -> checkArray(node, schema, path, componentSchemas, depth, problems);
            case OBJECT -> checkObject(node, schema, path, componentSchemas, depth, problems);
            default -> {
                //boolean values have no further constraints
            }
        }
        if (depth > 0) {
            checkEnum(node, schema, path, problems);
        }
    }

    /**
     * Object and array examples are sometimes written as embedded JSON strings, e.g. {@code example: '{"a":1}'}.
     */
    private static JsonNode parseEmbeddedJson(JsonNode value) {
        if (!value.isTextual()) {
            return value;
        }
        try {
            JsonNode parsed = Json.mapper().readTree(value.asText());
            return parsed != null && parsed.isContainerNode() ? parsed : value;
        } catch (JsonProcessingException _) {
            return value;
        }
    }

    private static Set<String> declaredTypes(Schema<?> schema) {
        Set<String> types = !CatsUtil.isEmpty(schema.getTypes())
                ? new LinkedHashSet<>(schema.getTypes())
                : new LinkedHashSet<>(schema.getType() != null ? Set.of(schema.getType()) : Set.of());
        types.remove("null");
        return types;
    }

    /**
     * Unquoted YAML scalars such as {@code example: 7} for a string schema are a common authoring style, not a bug.
     */
    private static boolean isCoercibleScalar(JsonNode node, Set<String> types) {
        return types.contains(STRING) && (node.isNumber() || node.isBoolean());
    }

    private static String jsonType(JsonNode node) {
        if (node.isTextual()) {
            return STRING;
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() || node.decimalValue().stripTrailingZeros().scale() <= 0 ? INTEGER : NUMBER;
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return node.isArray() ? ARRAY : OBJECT;
    }

    private static boolean isTypeAccepted(String actualType, Set<String> types) {
        return types.contains(actualType) || (INTEGER.equals(actualType) && types.contains(NUMBER));
    }

    private static void checkString(String value, Schema<?> schema, String path, List<String> problems) {
        int length = value.codePointCount(0, value.length());
        if (schema.getMinLength() != null && length < schema.getMinLength()) {
            problems.add("%slength %d is less than minLength %d".formatted(at(path), length, schema.getMinLength()));
        }
        if (schema.getMaxLength() != null && length > schema.getMaxLength()) {
            problems.add("%slength %d is greater than maxLength %d".formatted(at(path), length, schema.getMaxLength()));
        }
        if (StringUtils.isNotBlank(schema.getPattern()) && !matchesPattern(schema.getPattern(), value)) {
            problems.add("%sdoes not match pattern '%s'".formatted(at(path), schema.getPattern()));
        }
        if (!CatsModelUtils.isValueMatchingFormat(schema, value)) {
            problems.add("%sis not a valid '%s'".formatted(at(path), schema.getFormat()));
        }
    }

    /**
     * JSON Schema patterns are not implicitly anchored. Invalid patterns are reported by {@link ContradictorySchemaConstraintsLinter}.
     */
    private static boolean matchesPattern(String pattern, String value) {
        try {
            return Pattern.compile(pattern).matcher(value).find();
        } catch (PatternSyntaxException _) {
            return true;
        }
    }

    private static void checkNumber(BigDecimal value, Schema<?> schema, String path, List<String> problems) {
        BigDecimal minimum = schema.getMinimum();
        if (minimum != null) {
            boolean exclusive = Boolean.TRUE.equals(schema.getExclusiveMinimum());
            if (value.compareTo(minimum) < 0 || (exclusive && value.compareTo(minimum) == 0)) {
                problems.add("%s%s is less than %sminimum %s".formatted(at(path), value, exclusive ? "exclusive " : "", minimum));
            }
        }
        if (schema.getExclusiveMinimumValue() != null && value.compareTo(schema.getExclusiveMinimumValue()) <= 0) {
            problems.add("%s%s is not greater than exclusiveMinimum %s".formatted(at(path), value, schema.getExclusiveMinimumValue()));
        }
        BigDecimal maximum = schema.getMaximum();
        if (maximum != null) {
            boolean exclusive = Boolean.TRUE.equals(schema.getExclusiveMaximum());
            if (value.compareTo(maximum) > 0 || (exclusive && value.compareTo(maximum) == 0)) {
                problems.add("%s%s is greater than %smaximum %s".formatted(at(path), value, exclusive ? "exclusive " : "", maximum));
            }
        }
        if (schema.getExclusiveMaximumValue() != null && value.compareTo(schema.getExclusiveMaximumValue()) >= 0) {
            problems.add("%s%s is not less than exclusiveMaximum %s".formatted(at(path), value, schema.getExclusiveMaximumValue()));
        }
        if (schema.getMultipleOf() != null && schema.getMultipleOf().signum() > 0
                && value.remainder(schema.getMultipleOf()).signum() != 0) {
            problems.add("%s%s is not a multiple of %s".formatted(at(path), value, schema.getMultipleOf()));
        }
    }

    @SuppressWarnings("rawtypes")
    private static void checkArray(JsonNode node, Schema<?> schema, String path, Map<String, Schema> componentSchemas,
                                   int depth, List<String> problems) {
        if (schema.getMinItems() != null && node.size() < schema.getMinItems()) {
            problems.add("%shas %d items, less than minItems %d".formatted(at(path), node.size(), schema.getMinItems()));
        }
        if (schema.getMaxItems() != null && node.size() > schema.getMaxItems()) {
            problems.add("%shas %d items, more than maxItems %d".formatted(at(path), node.size(), schema.getMaxItems()));
        }
        if (Boolean.TRUE.equals(schema.getUniqueItems())) {
            Set<JsonNode> seen = new HashSet<>();
            for (JsonNode item : node) {
                if (!seen.add(item)) {
                    problems.add("%scontains duplicate items but uniqueItems is true".formatted(at(path)));
                    break;
                }
            }
        }
        if (schema.getItems() != null) {
            for (int i = 0; i < node.size(); i++) {
                validate(node.get(i), schema.getItems(), "%s[%d]".formatted(path, i), componentSchemas, depth + 1, problems);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void checkObject(JsonNode node, Schema<?> schema, String path, Map<String, Schema> componentSchemas,
                                    int depth, List<String> problems) {
        if (schema.getProperties() == null) {
            return;
        }
        ((Map<String, Schema>) schema.getProperties()).forEach((propertyName, propertySchema) -> {
            if (node.has(propertyName)) {
                String propertyPath = path.isEmpty() ? propertyName : path + "." + propertyName;
                validate(node.get(propertyName), propertySchema, propertyPath, componentSchemas, depth + 1, problems);
            }
        });
    }

    private static void checkEnum(JsonNode node, Schema<?> schema, String path, List<String> problems) {
        if (CatsUtil.isEmpty(schema.getEnum()) || !node.isValueNode()) {
            return;
        }
        boolean allowed = schema.getEnum().stream().anyMatch(enumValue -> isSameValue(node, enumValue));
        if (!allowed) {
            problems.add("%s%s is not one of %s".formatted(at(path), node, schema.getEnum().stream()
                    .map(String::valueOf).toList()));
        }
    }

    private static boolean isSameValue(JsonNode node, Object enumValue) {
        if (enumValue == null) {
            return false;
        }
        if (node.isNumber() && enumValue instanceof Number number) {
            return node.decimalValue().compareTo(new BigDecimal(number.toString())) == 0;
        }
        return node.asText().equals(String.valueOf(enumValue));
    }

    private static String at(String path) {
        return path.isEmpty() ? "value " : "'%s' ".formatted(path);
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "schema-value-conformance-linter";
    }

    @Override
    public String description() {
        return "detects default and example values that violate the type, format, length, range, pattern or item constraints of their own schema";
    }
}
