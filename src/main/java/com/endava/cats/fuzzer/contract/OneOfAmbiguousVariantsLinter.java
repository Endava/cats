package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.OpenApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects {@code oneOf} schemas without a discriminator where one variant accepts every minimal valid instance
 * of another variant. Such payloads match more than one variant and are therefore rejected by {@code oneOf}.
 * <p>
 * The analysis is conservative: whenever a variant uses a construct that could make the variants disjoint
 * (value constraints on shared properties, {@code not}, nested composition, conditional keywords, etc.),
 * the pair is considered unambiguous.
 */
@Linter
@Singleton
public class OneOfAmbiguousVariantsLinter extends BaseLinter {
    private static final int MAX_DEPTH = 10;
    private static final String OBJECT = "object";
    private static final String NULL = "null";
    private static final String INTEGER = "integer";
    private static final String NUMBER = "number";
    private static final Set<String> PRIMITIVE_TYPES = Set.of("string", INTEGER, NUMBER, "boolean", NULL);

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new OneOfAmbiguousVariantsLinter instance.
     *
     * @param tcl the test case listener
     */
    public OneOfAmbiguousVariantsLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that oneOf schemas without a discriminator do not contain variants that accept every instance of another variant");
        testCaseListener.addExpectedResult(log, "Each payload valid for a oneOf variant must match exactly one variant");
        super.addDefaultsForPathAgnosticFuzzers();

        if (data.getOpenApi() == null) {
            testCaseListener.skipTest(log, "OpenAPI specification not available");
            return;
        }

        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(data.getOpenApi());
        Set<String> violations = new LinkedHashSet<>();
        SchemaWalker.walk(data.getOpenApi(), (loc, schema) -> inspectOneOf(loc, schema, componentSchemas, violations));

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All oneOf schemas have mutually exclusive variants");
        } else {
            testCaseListener.reportResultError(log, data, "Ambiguous oneOf variants found", String.join("\n", violations));
        }
    }

    @SuppressWarnings("rawtypes")
    private static void inspectOneOf(SchemaLocation loc, Schema<?> schema, Map<String, Schema> componentSchemas, Set<String> violations) {
        if (schema == null || schema.getDiscriminator() != null || CatsUtil.isEmpty(schema.getOneOf()) || schema.getOneOf().size() < 2) {
            return;
        }
        List<Schema> oneOf = schema.getOneOf();
        List<Variant> variants = new ArrayList<>();
        for (int i = 0; i < oneOf.size(); i++) {
            variants.add(Variant.from(oneOf.get(i), variantName(oneOf.get(i), i), componentSchemas));
        }

        for (int i = 0; i < variants.size(); i++) {
            for (int j = 0; j < variants.size(); j++) {
                Variant subsumed = variants.get(i);
                Variant subsuming = variants.get(j);
                if (i != j && subsumed != null && subsuming != null
                        && subsuming.acceptsEveryMinimalInstanceOf(subsumed, componentSchemas)) {
                    violations.add(("Schema '%s' oneOf is ambiguous: every payload valid for '%s' also matches '%s' (e.g. %s). "
                            + "Add a discriminator, a distinguishing const/enum property, or additionalProperties: false")
                            .formatted(loc.fqn(), subsumed.name(), subsuming.name(), subsumed.minimalInstance(componentSchemas)));
                    return;
                }
            }
        }
    }

    private static String variantName(Schema<?> variant, int index) {
        return variant != null && variant.get$ref() != null
                ? CatsModelUtils.getSimpleRef(variant.get$ref())
                : "oneOf[%d]".formatted(index);
    }

    @SuppressWarnings("rawtypes")
    private static boolean areCompatible(Schema<?> subsumingProperty, Schema<?> subsumedProperty, Map<String, Schema> componentSchemas) {
        if (subsumingProperty == null || subsumedProperty == null) {
            return false;
        }
        if (subsumingProperty.get$ref() != null && subsumingProperty.get$ref().equals(subsumedProperty.get$ref())) {
            return true;
        }
        Schema<?> subsuming = CatsModelUtils.resolveSchema(subsumingProperty, componentSchemas);
        Schema<?> subsumed = CatsModelUtils.resolveSchema(subsumedProperty, componentSchemas);
        if (CatsModelUtils.isFreeFormSchema(subsuming)) {
            return true;
        }
        String subsumingType = hasValueConstraints(subsuming) || isStructural(subsuming) ? null : singlePrimitiveType(subsuming);
        String subsumedType = isStructural(subsumed) ? null : singlePrimitiveType(subsumed);
        return isTypeAccepted(subsumingType, subsumedType);
    }

    private static boolean isTypeAccepted(String subsumingType, String subsumedType) {
        return subsumingType != null && subsumedType != null
                && (subsumingType.equals(subsumedType) || (NUMBER.equals(subsumingType) && INTEGER.equals(subsumedType)));
    }

    private static boolean isStructural(Schema<?> schema) {
        return schema.get$ref() != null || CatsModelUtils.isComposedSchema(schema) || schema.getNot() != null
                || !CatsUtil.isEmpty(schema.getProperties()) || schema.getItems() != null;
    }

    private static String singlePrimitiveType(Schema<?> schema) {
        Set<String> nonNullTypes = declaredTypes(schema).stream().filter(type -> !NULL.equals(type)).collect(Collectors.toSet());
        if (nonNullTypes.size() == 1 && PRIMITIVE_TYPES.contains(nonNullTypes.iterator().next())) {
            return nonNullTypes.iterator().next();
        }
        return nonNullTypes.isEmpty() && declaredTypes(schema).contains(NULL) ? NULL : null;
    }

    private static Set<String> declaredTypes(Schema<?> schema) {
        if (!CatsUtil.isEmpty(schema.getTypes())) {
            return new LinkedHashSet<>(schema.getTypes());
        }
        return schema.getType() != null ? new LinkedHashSet<>(Set.of(schema.getType())) : new LinkedHashSet<>();
    }

    private static boolean hasValueConstraints(Schema<?> schema) {
        return !CatsUtil.isEmpty(schema.getEnum()) || schema.getConst() != null || schema.getPattern() != null
                || schema.getFormat() != null || schema.getMinimum() != null || schema.getMaximum() != null
                || schema.getExclusiveMinimumValue() != null || schema.getExclusiveMaximumValue() != null
                || schema.getMinLength() != null || schema.getMaxLength() != null || schema.getMultipleOf() != null
                || schema.getMinItems() != null || schema.getMaxItems() != null || schema.getUniqueItems() != null;
    }

    private static boolean hasUnsupportedKeywords(Schema<?> schema) {
        return schema.getDiscriminator() != null || !CatsUtil.isEmpty(schema.getOneOf()) || !CatsUtil.isEmpty(schema.getAnyOf())
                || schema.getNot() != null || schema.getIf() != null || !CatsUtil.isEmpty(schema.getPatternProperties())
                || !CatsUtil.isEmpty(schema.getDependentRequired()) || !CatsUtil.isEmpty(schema.getDependentSchemas())
                || schema.getPropertyNames() != null || schema.getUnevaluatedProperties() != null
                || schema.getMinProperties() != null || schema.getMaxProperties() != null;
    }

    /**
     * A flattened (allOf-merged) view of a oneOf variant.
     */
    @SuppressWarnings("rawtypes")
    private record Variant(String name, Set<String> types, Map<String, Schema> properties, Set<String> required,
                           boolean closed, boolean constrained) {

        static Variant from(Schema<?> raw, String name, Map<String, Schema> componentSchemas) {
            Accumulator accumulator = new Accumulator();
            if (!accumulator.merge(raw, componentSchemas, Collections.newSetFromMap(new IdentityHashMap<>()), 0)) {
                return null;
            }
            Set<String> types = accumulator.types;
            if (types == null) {
                if (accumulator.properties.isEmpty() && accumulator.required.isEmpty()) {
                    return null;
                }
                types = Set.of(OBJECT);
            }
            return new Variant(name, types, accumulator.properties, accumulator.required, accumulator.closed, accumulator.constrained);
        }

        boolean isObject() {
            return types.contains(OBJECT) && types.stream().allMatch(type -> OBJECT.equals(type) || NULL.equals(type));
        }

        String primitiveType() {
            if (!properties.isEmpty() || !required.isEmpty()) {
                return null;
            }
            Set<String> nonNullTypes = types.stream().filter(type -> !NULL.equals(type)).collect(Collectors.toSet());
            if (nonNullTypes.size() == 1 && PRIMITIVE_TYPES.contains(nonNullTypes.iterator().next())) {
                return nonNullTypes.iterator().next();
            }
            return nonNullTypes.isEmpty() && types.contains(NULL) ? NULL : null;
        }

        /**
         * Returns true if this variant accepts every instance of {@code other} that only contains its required properties.
         */
        boolean acceptsEveryMinimalInstanceOf(Variant other, Map<String, Schema> componentSchemas) {
            if (constrained) {
                return false;
            }
            if (isObject() && other.isObject()) {
                if (!other.required.containsAll(required) || (closed && !properties.keySet().containsAll(other.required))) {
                    return false;
                }
                return other.required.stream().allMatch(propertyName -> !properties.containsKey(propertyName)
                        || (other.properties.containsKey(propertyName)
                        && areCompatible(properties.get(propertyName), other.properties.get(propertyName), componentSchemas)));
            }
            return isTypeAccepted(primitiveType(), other.primitiveType());
        }

        String minimalInstance(Map<String, Schema> componentSchemas) {
            if (!isObject()) {
                return constrained ? "any valid %s value".formatted(primitiveType()) : sampleValue(primitiveType()).toString();
            }
            ObjectNode instance = JsonNodeFactory.instance.objectNode();
            for (String propertyName : required) {
                Schema<?> property = CatsModelUtils.resolveSchema(properties.get(propertyName), componentSchemas);
                instance.set(propertyName, sampleValue(CatsModelUtils.getPrimaryType(property)));
            }
            return instance.toString();
        }

        private static JsonNode sampleValue(String type) {
            JsonNodeFactory factory = JsonNodeFactory.instance;
            return switch (type == null ? OBJECT : type) {
                case "string" -> factory.textNode("string");
                case INTEGER, NUMBER -> factory.numberNode(1);
                case "boolean" -> factory.booleanNode(true);
                case "array" -> factory.arrayNode();
                case NULL -> factory.nullNode();
                default -> factory.objectNode();
            };
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class Accumulator {
        private Set<String> types;
        private final Map<String, Schema> properties = new LinkedHashMap<>();
        private final Set<String> required = new LinkedHashSet<>();
        private boolean closed;
        private boolean constrained;

        @SuppressWarnings("unchecked")
        boolean merge(Schema<?> raw, Map<String, Schema> componentSchemas, Set<Schema<?>> visited, int depth) {
            Schema<?> schema = CatsModelUtils.resolveSchema(raw, componentSchemas);
            if (schema == null || schema.get$ref() != null || depth > MAX_DEPTH || !visited.add(schema) || hasUnsupportedKeywords(schema)) {
                return false;
            }
            if (!mergeTypes(declaredTypes(schema)) || !mergeAdditionalProperties(schema.getAdditionalProperties())) {
                return false;
            }
            if (schema.getProperties() != null) {
                ((Map<String, Schema>) schema.getProperties()).forEach(properties::putIfAbsent);
            }
            if (schema.getRequired() != null) {
                required.addAll(schema.getRequired());
            }
            constrained |= hasValueConstraints(schema);
            for (Schema<?> allOfItem : CatsUtil.isEmpty(schema.getAllOf()) ? List.<Schema>of() : schema.getAllOf()) {
                if (!merge(allOfItem, componentSchemas, visited, depth + 1)) {
                    return false;
                }
            }
            return true;
        }

        private boolean mergeTypes(Set<String> schemaTypes) {
            if (schemaTypes.isEmpty()) {
                return true;
            }
            if (types == null) {
                types = schemaTypes;
                return true;
            }
            types = types.stream().filter(schemaTypes::contains).collect(Collectors.toSet());
            return !types.isEmpty();
        }

        private boolean mergeAdditionalProperties(Object additionalProperties) {
            if (Boolean.FALSE.equals(additionalProperties)
                    || (additionalProperties instanceof Schema<?> apSchema && Boolean.FALSE.equals(apSchema.getBooleanSchemaValue()))) {
                closed = true;
                return true;
            }
            return !(additionalProperties instanceof Schema<?> apSchema) || CatsModelUtils.isFreeFormSchema(apSchema);
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "oneof-ambiguous-variants-linter";
    }

    @Override
    public String description() {
        return "detects oneOf schemas without a discriminator where one variant accepts every instance of another variant, making payloads match more than one variant";
    }
}
