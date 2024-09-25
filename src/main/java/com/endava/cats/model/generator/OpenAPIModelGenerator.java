package com.endava.cats.model.generator;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.DepthLimitingSerializer;
import com.endava.cats.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.endava.cats.generator.simple.StringGenerator.generateValueBasedOnMinMax;

/**
 * A modified version of {@code io.swagger.codegen.examples.ExampleGenerator} that takes into consideration several other request
 * setups including complex objects and array of objects.
 * <p>
 * This is a stateful object. Don't use it through dependency injection.
 * <p>
 * Supported String formats:
 * <ul>
 *     <li>uuid</li>
 *     <li>date</li>
 *     <li>date-time</li>
 *     <li>email</li>
 *     <li>ip</li>
 *     <li>ipv4</li>
 *     <li>ipv6</li>
 *     <li>password</li>
 *     <li>byte</li>
 *     <li>uri/url</li>
 * </ul>
 */
public class OpenAPIModelGenerator {
    private static final String EXAMPLE = "example";
    public static final String SYNTH_SCHEMA_NAME = "CatsGetSchema";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final BigDecimal MAX = new BigDecimal("99999999999");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(OpenAPIModelGenerator.class);
    private final Set<Schema<?>> catsGeneratedExamples = new HashSet<>();
    private final Random random;
    private final boolean useExamples;
    private final CatsGlobalContext globalContext;
    private final ValidDataFormat validDataFormat;
    private final int selfReferenceDepth;
    private String currentProperty = "";
    private final Map<String, String> schemaRefMap;
    private final Map<String, Integer> callStackCounter;
    private final boolean useDefaults;
    private final int arraySize;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs an OpenAPIModelGenerator with the specified configuration.
     *
     * @param catsGlobalContext   The global context for CATS.
     * @param validDataFormat     The format to use for generating valid data.
     * @param useExamplesArgument Flag indicating whether to use examples from the OpenAPI specification.
     * @param selfReferenceDepth  The maximum depth for generating self-referencing models.
     */
    public OpenAPIModelGenerator(CatsGlobalContext catsGlobalContext, ValidDataFormat validDataFormat, boolean useExamplesArgument, int selfReferenceDepth, boolean useDefaults, int arraySize) {
        this.globalContext = catsGlobalContext;
        this.random = CatsUtil.random();
        this.useExamples = useExamplesArgument;
        this.selfReferenceDepth = selfReferenceDepth;
        this.validDataFormat = validDataFormat;
        this.schemaRefMap = new LinkedHashMap<>();
        this.callStackCounter = new HashMap<>();
        this.useDefaults = useDefaults;
        this.arraySize = arraySize;
        SimpleModule module = new SimpleModule();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);  // Exclude null values
        module.addSerializer(Object.class, new DepthLimitingSerializer());
        mapper.registerModule(module);
    }


    /**
     * Generates an example map for the specified model name.
     *
     * @param modelName The name of the OpenAPI model.
     * @return A map containing the example JSON representation of the model.
     * The map key is "example" and the value is the pretty-printed JSON.
     * Returns an empty map if the model is not found or an example cannot be generated.
     */
    public Map<String, String> generate(String modelName) {
        Map<String, String> kv = new HashMap<>();
        if (modelName != null) {
            final Schema schema = this.globalContext.getSchemaFromReference(modelName);
            if (schema != null) {
                Object exampleObject = this.resolveModelToExample(modelName, schema);
                String example = tryToSerializeExample(exampleObject);

                if (example == null) {
                    example = writeObjectAsString(exampleObject);
                    globalContext.recordError("Generate sample it's too large to be processed in memory. CATS used a limiting depth serializer which might not include all expected fields. Re-run CATS with a smaller --selfReferenceDepth value, like --selfReferenceDepth 2");
                }

                if (example != null) {
                    kv.put(EXAMPLE, example);
                    return Map.copyOf(kv);
                }
            }
        }
        schemaRefMap.clear();
        currentProperty = "";
        return Collections.emptyMap();
    }

    private String writeObjectAsString(Object exampleObject) {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonGenerator jsonGenerator = mapper.getFactory().createGenerator(stringWriter);
            mapper.writeValue(jsonGenerator, exampleObject);
            return stringWriter.toString();
        } catch (IOException e) {
            logger.debug("Error writing large object as string: {}", e.getMessage());
            return null;
        }
    }

    public String tryToSerializeExample(Object obj) {
        try {
            return Json.pretty().writeValueAsString(obj);
        } catch (Exception e) {
            logger.debug("Generated object is too large. Switching to custom depth aware serializer...");
            return null;
        }
    }

    private <T> Object resolvePropertyToExample(String propertyName, Schema<T> propertySchema) {
        if (JsonUtils.isCyclicSchemaReference(currentProperty, schemaRefMap, selfReferenceDepth)) {
            return null;
        }

        Object enumOrDefault = this.getEnumOrDefault(propertySchema);

        if (enumOrDefault != null) {
            return enumOrDefault;
        }

        Object generatedValueFromFormat = this.generateStringValue(propertyName, propertySchema);

        if (generatedValueFromFormat != null) {
            return generatedValueFromFormat;
        } else if (getExample(propertySchema) != null && canUseExamples(propertySchema)) {
            logger.trace("Example set in swagger spec, returning example: '{}'", propertySchema.getExample());
            return this.formatExampleIfNeeded(propertySchema);
        } else if (CatsModelUtils.isStringSchema(propertySchema)) {
            return this.getExampleFromStringSchema(propertyName, propertySchema);
        } else if (CatsModelUtils.isBooleanSchema(propertySchema)) {
            return this.getExampleFromBooleanSchema();
        } else if (CatsModelUtils.isArraySchema(propertySchema)) {
            return this.getExampleFromArraySchema(propertyName, propertySchema);
        } else if (CatsModelUtils.isNumberSchema(propertySchema)) {
            return this.getExampleFromNumberSchema(propertySchema);
        } else if (CatsModelUtils.isIntegerSchema(propertySchema)) {
            return this.getExampleFromIntegerSchema(propertySchema);
        } else if (CatsModelUtils.isObjectSchema(propertySchema)) {
            return this.getExampleForObjectSchema(propertySchema);
        } else if (propertySchema.getAdditionalProperties() instanceof Schema) {
            return this.getExampleFromAdditionalPropertiesSchema(propertyName, propertySchema);
        }

        return resolveProperties(propertySchema);
    }

    private Object getExample(Schema<?> schema) {
        if (schema.getExample() != null) {
            return schema.getExample();
        }
        if (schema.getExamples() != null && !schema.getExamples().isEmpty()) {
            return schema.getExamples().getFirst();
        }
        return null;
    }

    private <T> Object generateStringValue(String propertyName, Schema<T> propertySchema) {
        if (syntheticSchema(propertyName) || !CatsModelUtils.isStringSchema(propertySchema)) {
            return null;
        }

        String propertyForGeneration = currentProperty.endsWith(propertyName) ? currentProperty : currentProperty + "#" + propertyName;
        return validDataFormat.generate(propertySchema, propertyForGeneration);
    }

    private <T> Object getEnumOrDefault(Schema<T> propertySchema) {
        List<T> enumValues = propertySchema.getEnum();
        if (!CollectionUtils.isEmpty(enumValues)) {
            return enumValues.stream().filter(Objects::nonNull).findAny().orElse(enumValues.getFirst());
        }

        if (propertySchema.getDefault() != null && useDefaults) {
            return propertySchema.getDefault();
        }

        return null;
    }

    private boolean syntheticSchema(String currentProperty) {
        return currentProperty.startsWith(SYNTH_SCHEMA_NAME);
    }

    <T> Object formatExampleIfNeeded(Schema<T> property) {
        Object example = getExample(property);
        if (example == null) {
            return null;
        }
        if (CatsModelUtils.isDateSchema(property)) {
            return DATE_FORMATTER.format(LocalDate.ofInstant(((Date) example).toInstant(), ZoneId.systemDefault()));
        }
        if (CatsModelUtils.isDateTimeSchema(property)) {
            return ((OffsetDateTime) example).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (CatsModelUtils.isBinarySchema(property) || CatsModelUtils.isByteArraySchema(property)) {
            try {
                return Base64.getDecoder().decode((byte[]) example);
            } catch (IllegalArgumentException e) {
                return example;
            }
        }
        if (String.valueOf(example).contains("\n")) {
            return String.valueOf(example).replace("\n", "");
        }
        return example;
    }

    private Object resolveProperties(Schema<?> schema) {
        if (schema.getProperties() != null) {
            String previousPropertyValue = currentProperty;

            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
                currentProperty = StringUtils.isBlank(previousPropertyValue) ? String.valueOf(property.getKey()) : previousPropertyValue + "#" + property.getKey();
                result.put(property.getKey(), this.resolvePropertyToExample(property.getKey(), property.getValue()));
            }

            currentProperty = previousPropertyValue;
            return result;
        }
        return getExample(schema);
    }

    private <T> boolean canUseExamples(Schema<T> property) {
        return useExamples || catsGeneratedExamples.contains(property);
    }

    private <T> Object getExampleForObjectSchema(Schema<T> property) {
        Object example = getExample(property);
        if (example != null) {
            return example;
        }

        if (property.getProperties() == null || property.getProperties().isEmpty()) {
            return new HashMap<>();
        }
        return resolveProperties(property);
    }

    private <T> Object getExampleFromStringSchema(String propertyName, Schema<T> property) {
        logger.trace("String property {}", propertyName);

        if (property.getMinLength() != null || property.getMaxLength() != null) {
            return generateValueBasedOnMinMax(property);
        }
        if (CatsModelUtils.isDecimalSchema(property)) {
            return generateBigDecimal(property);
        }
        if (property.getPattern() != null) {
            return StringGenerator.generate(property.getPattern(), -1, -1);
        }
        logger.trace("No constraints, generating alphanumeric string based on property length {}", propertyName);
        return StringGenerator.generate(StringGenerator.ALPHANUMERIC_PLUS, propertyName.length(), propertyName.length() + 4);
    }

    private BigDecimal generateBigDecimal(Schema<?> schema) {
        BigDecimal min = schema.getMinimum() != null ? schema.getMinimum() : BigDecimal.ONE;
        BigDecimal max = schema.getMaximum() != null ? schema.getMaximum() : MAX;

        BigDecimal range = max.subtract(min);
        BigDecimal randomBigDecimal = min.add(range.multiply(BigDecimal.valueOf(random.nextDouble())));

        randomBigDecimal = randomBigDecimal.setScale(2, RoundingMode.HALF_UP);

        return randomBigDecimal;
    }

    Map<String, Object> getExampleFromAdditionalPropertiesSchema(String propertyName, Schema property) {
        if (globalContext.getAdditionalProperties().containsKey(propertyName)) {
            return globalContext.getAdditionalProperties().get(propertyName);
        }
        Map<String, Object> mp = new HashMap<>();

        if (property.getName() != null) {
            mp.put(property.getName(), resolvePropertyToExample(propertyName, (Schema) property.getAdditionalProperties()));
        } else if (((Schema) property.getAdditionalProperties()).get$ref() != null) {
            Schema innerSchema = (Schema) property.getAdditionalProperties();
            Schema addPropSchema = this.globalContext.getSchemaFromReference(innerSchema.get$ref());
            if (isCyclicAdditionalPropertiesCall()) {
                return Map.of();
            }
            mp.put("key", resolvePropertyToExample(propertyName, addPropSchema));
        } else {
            mp.put("key", resolvePropertyToExample(propertyName, (Schema) property.getAdditionalProperties()));
        }

        globalContext.getAdditionalProperties().put(propertyName, mp);
        return mp;
    }

    /**
     * This is only used for additionalProperties that are refer themselves.
     *
     * @return true if this is a cyclic call, false otherwise
     */
    private boolean isCyclicAdditionalPropertiesCall() {
        int currentCount = callStackCounter.getOrDefault(currentProperty, 0);
        if (currentCount > selfReferenceDepth) {
            callStackCounter.remove(currentProperty);
            return true;
        }
        callStackCounter.put(currentProperty, currentCount + 1);
        return false;
    }

    private Object getExampleFromIntegerSchema(Schema<?> property) {
        Double min = property.getMinimum() == null ? null : property.getMinimum().doubleValue();
        Double max = property.getMaximum() == null ? null : property.getMaximum().doubleValue();

        if (CatsModelUtils.isShortIntegerSchema(property)) {
            return (int) randomNumber(min, max);
        }
        return (long) randomNumber(min, max);
    }

    private Object getExampleFromNumberSchema(Schema<?> property) {
        Double min = property.getMinimum() == null ? null : property.getMinimum().doubleValue();
        Double max = property.getMaximum() == null ? null : property.getMaximum().doubleValue();

        if (CatsModelUtils.isFloatSchema(property)) {
            return (float) randomNumber(min, max);
        }

        return randomNumber(min, max);
    }

    private Object getExampleFromBooleanSchema() {
        return random.nextBoolean();
    }

    private Object getExampleFromArraySchema(String propertyName, Schema property) {
        Schema innerType = property.getItems();
        if (innerType == null) {
            return "[]";
        }
        if (innerType.get$ref() != null) {
            schemaRefMap.put(currentProperty, innerType.get$ref());
            Object potentialParam = this.globalContext.getObjectFromPathsReference(innerType.get$ref());
            if (potentialParam instanceof Parameter parameter) {
                if (CatsModelUtils.isArraySchema(parameter.getSchema())) {
                    return getExampleFromArraySchema(propertyName, parameter.getSchema());
                } else {
                    innerType = parameter.getSchema();
                }
            } else {
                innerType = this.globalContext.getSchemaFromReference(innerType.get$ref());
            }
        }
        if (innerType != null) {
            int arrayLength = getArrayLength(property);
            Object[] objectProperties = new Object[arrayLength];

            for (int i = 0; i < arrayLength; i++) {
                objectProperties[i] = generateArrayElement(propertyName, innerType);
            }
            return objectProperties;
        }
        return "[]";
    }

    private Object generateArrayElement(String propertyName, Schema innerType) {
        boolean isNullSchema = isNullSchema(innerType);
        if (isNullSchema) {
            return StringGenerator.generate(StringGenerator.ALPHANUMERIC_PLUS, propertyName.length(), propertyName.length() + 4);
        } else {
            return resolveModelToExample(propertyName, innerType);
        }
    }

    private static boolean isNullSchema(Schema innerType) {
        return innerType.getType() == null && innerType.get$ref() == null && !CatsModelUtils.isComposedSchema(innerType) && innerType.getProperties() == null;
    }

    int getArrayLength(Schema<?> property) {
        int min = null == property.getMinItems() ? 1 : property.getMinItems();
        int max = null == property.getMaxItems() ? 2 : property.getMaxItems();

        return Math.max(min, Math.min(this.arraySize, max));
    }

    double randomNumber(Double min, Double max) {
        if (min != null && max != null) {
            double range = max - min;
            return random.nextDouble() * range + min;
        } else if (min != null) {
            return random.nextDouble() + min;
        } else if (max != null) {
            return random.nextDouble() * max;
        } else {
            return random.nextDouble() * 10;
        }
    }

    private Object resolveModelToExample(String name, Schema schema) {
        /* When checking for cyclic references make sure we exclude schema names. Some OpenAPI specs are generated by frameworks,
        so they might generate schema names that might seem cyclic such as: Body_Create_a_previously_generated_voice_v1_voice_generation_create_voice_post
         */
        if (JsonUtils.isCyclicReference(name, selfReferenceDepth) && globalContext.getSchemaFromReference(name) == null) {
            return null;
        }

        Map<String, Object> values = new HashMap<>();
        logger.trace("Resolving model '{}' to example", name);
        schema = normalizeDiscriminatorMappingsToOneOf(name, schema);

        String schemaRef = schema.get$ref();
        if (schemaRef != null) {
            schema = globalContext.getSchemaFromReference(schemaRef);
        }

        if (schema.getProperties() != null) {
            logger.trace("Schema properties not null {}: {}", name, schema.getProperties().keySet());
            this.processSchemaProperties(name, schema, values);
        }
        if (CatsModelUtils.isComposedSchema(schema)) {
            this.populateWithComposedSchema(values, name, schema);
            return values;
        } else {
            globalContext.getRequestDataTypes().put(currentProperty, schema);
            if (!values.isEmpty()) {
                return values;
            }
            return this.resolvePropertyToExample(name, schema);
        }
    }

    private void processSchemaProperties(String name, Schema schema, Map<String, Object> values) {
        if (JsonUtils.isCyclicReference(name, selfReferenceDepth)) {
            return;
        }

        logger.trace("Creating example from model values {}", name);

        if (schema.getDiscriminator() != null) {
            globalContext.getDiscriminators().add(schema.getDiscriminator());
        }
        String previousPropertyValue = currentProperty;

        for (Object propertyName : schema.getProperties().keySet()) {
            if (JsonUtils.isCyclicSchemaReference(currentProperty, schemaRefMap, selfReferenceDepth)
                    || JsonUtils.isCyclicReference(currentProperty, selfReferenceDepth)) {
                return;
            }
            if (propertyName == null) {
                continue;
            }

            String schemaRef = ((Schema) schema.getProperties().get(propertyName)).get$ref();

            Schema innerSchema = this.globalContext.getSchemaFromReference(schemaRef != null ? schemaRef : "");
            currentProperty = StringUtils.isBlank(previousPropertyValue) ? String.valueOf(propertyName) : previousPropertyValue + "#" + propertyName;

            if (schemaRef != null) {
                schemaRefMap.put(currentProperty, schemaRef);
            }

            processInnerSchema(name, schema, values, propertyName, innerSchema);
        }
        currentProperty = previousPropertyValue;
//        schema.setExample(values);
        catsGeneratedExamples.add(schema);
    }

    private void processInnerSchema(String name, Schema schema, Map<String, Object> values, Object
            propertyName, Schema innerSchema) {
        if (innerSchema == null) {
            this.parseFromInnerSchema(name, schema, values, propertyName);
        } else if (schema.getDiscriminator() != null && schema.getDiscriminator().getPropertyName().equalsIgnoreCase(propertyName.toString())) {
            values.put(propertyName.toString(), this.matchToEnumOrEmpty(name, innerSchema, propertyName.toString()));
        } else {
            values.put(propertyName.toString(), this.resolveModelToExample(propertyName.toString(), innerSchema));
        }
    }

    private Schema normalizeDiscriminatorMappingsToOneOf(String name, Schema<?> schema) {
        if (schema.getDiscriminator() != null && !CollectionUtils.isEmpty(schema.getDiscriminator().getMapping())
                && !CatsModelUtils.isComposedSchema(schema) && !isAnyComposedSchemaInChain(name)) {
            Schema<?> composedSchema = new Schema<>();
            composedSchema.setOneOf(schema.getDiscriminator().getMapping().values()
                    .stream()
                    .filter(schemaName -> !schemaName.equalsIgnoreCase(name))
                    .map(schemaName -> new Schema<>().$ref(schemaName))
                    .toList());
            composedSchema.setDiscriminator(schema.getDiscriminator());
            schema.getProperties().get(schema.getDiscriminator().getPropertyName()).setEnum(new ArrayList<>(schema.getDiscriminator().getMapping().keySet()));
            globalContext.getDiscriminators().add(schema.getDiscriminator());
            Schema<?> newSchema = Json.mapper().convertValue(schema, Schema.class);
            newSchema.setName("CatsChanged" + name);
            newSchema.getDiscriminator().setMapping(null);
            globalContext.getSchemaMap().put(newSchema.getName(), newSchema);
            for (String oneOfSchemaRef : schema.getDiscriminator().getMapping().values()) {
                String oneOfSchema = CatsModelUtils.getSimpleRef(oneOfSchemaRef);
                Schema<?> currentOneOfSchema = globalContext.getSchemaFromReference(oneOfSchema);

                if (currentOneOfSchema == null) {
                    currentOneOfSchema = new Schema<>();
                    currentOneOfSchema.set$ref(oneOfSchemaRef);
                    globalContext.getSchemaMap().put(oneOfSchema, currentOneOfSchema);
                }
                Optional.ofNullable(currentOneOfSchema.getAllOf()).orElse(Collections.emptyList())
                        .stream()
                        .filter(innerAllOfSchema -> innerAllOfSchema.get$ref() != null)
                        .forEach(innerAllOfSchema -> innerAllOfSchema.set$ref(newSchema.getName()));
            }
            return composedSchema;
        }
        return schema;
    }

    private boolean isAnyComposedSchemaInChain(String schemaChain) {
        String[] schemaRefs = schemaChain.split("_");
        return Arrays.stream(schemaRefs).anyMatch(entry -> CatsModelUtils.isComposedSchema(globalContext.getSchemaFromReference(entry)));
    }

    private void parseFromInnerSchema(String name, Schema schema, Map<String, Object> values, Object propertyName) {
        Schema innerSchema = (Schema) schema.getProperties().get(propertyName.toString());
        if (CatsModelUtils.isObjectSchema(innerSchema)) {
            values.put(propertyName.toString(), resolveModelToExample(propertyName.toString(), innerSchema));
        }
        if (CatsModelUtils.isComposedSchema(innerSchema)) {
            this.populateWithComposedSchema(values, propertyName.toString(), innerSchema);
        } else if (schema.getDiscriminator() != null && schema.getDiscriminator().getPropertyName().equalsIgnoreCase(propertyName.toString())) {
            values.put(propertyName.toString(), this.matchToEnumOrEmpty(name, innerSchema, propertyName.toString()));
            globalContext.getRequestDataTypes().put(currentProperty, innerSchema);
        } else {//maybe here a check for array schema
            logger.trace("Resolving {}", propertyName);
            Object example = this.resolvePropertyToExample(propertyName.toString(), innerSchema);
            values.put(propertyName.toString(), example);
            globalContext.getRequestDataTypes().put(currentProperty, innerSchema);
        }
    }

    private Object matchToEnumOrEmpty(String name, Schema innerSchema, String propertyName) {
        String result = Optional.ofNullable(innerSchema.getEnum()).orElse(List.of("")).stream().filter(value -> name.contains(value.toString())).findFirst().orElse("").toString();
        if (result.isEmpty()) {
            return globalContext.getDiscriminators()
                    .stream()
                    .filter(discriminator -> discriminator.getPropertyName().equalsIgnoreCase(propertyName) && discriminator.getMapping() != null)
                    .findFirst()
                    .orElse(new Discriminator().mapping(Collections.emptyMap()))
                    .getMapping().keySet().stream().filter(key -> name.toLowerCase().contains(key.toLowerCase()))
                    .findFirst()
                    .orElse("");
        }
        return result;
    }

    private void populateWithComposedSchema(Map<String, Object> values, String propertyName, Schema<?> composedSchema) {
        if (composedSchema.getAllOf() != null) {
            this.keepOriginalSchema(propertyName, composedSchema);

            addXXXOfExamples(values, propertyName, composedSchema.getAllOf(), "ALL_OF");
            String newKey = "ALL_OF";
            Map<String, Object> finalMap = new HashMap<>();

            boolean innerAllOff = isInnerAllOff(values);

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map map) {
                    finalMap.putAll(map);
                }
            }

            if (innerAllOff) {
                values.clear();
                values.put(newKey, finalMap);
            } else {
                values.put(propertyName, finalMap);
            }
            values = values.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            this.createMergedSchema(propertyName, composedSchema.getAllOf());
        }
        List<Schema> anyOfNonNullSchemas = this.excludeNullSchemas(composedSchema.getAnyOf());
        if (anyOfNonNullSchemas.size() == 1) {
            values.put(propertyName, resolveModelToExample(propertyName, anyOfNonNullSchemas.getFirst()));
        } else if (composedSchema.getAnyOf() != null) {
            mapDiscriminator(composedSchema, anyOfNonNullSchemas);
            addXXXOfExamples(values, propertyName, anyOfNonNullSchemas, "ANY_OF");
        }

        List<Schema> oneOfNonNullSchemas = this.excludeNullSchemas(composedSchema.getOneOf());
        if (oneOfNonNullSchemas.size() == 1) {
            values.put(propertyName, resolveModelToExample(propertyName, oneOfNonNullSchemas.getFirst()));
        } else if (composedSchema.getOneOf() != null) {
            mapDiscriminator(composedSchema, oneOfNonNullSchemas);
            addXXXOfExamples(values, propertyName, oneOfNonNullSchemas, "ONE_OF");
        }
    }

    /**
     * Keep the original schema in the global context for future reference. This is useful when using cross path references
     * which will reference the original schema which needs to be a ComposedSchema. Something like: #/paths/~1application.list/post/requestBody/content/application~1json/schema/allOf/0.
     *
     * @param propertyName   the name of the property
     * @param composedSchema the composed schema
     */
    private void keepOriginalSchema(String propertyName, Schema<?> composedSchema) {
        String keyToKeep = CatsModelUtils.getSimpleRef(propertyName) + CatsGlobalContext.ORIGINAL;
        globalContext.getSchemaMap().put(keyToKeep, composedSchema);
    }

    private static boolean isInnerAllOff(Map<String, Object> values) {
        /* if it's an allOf relation referring a property from another object*/
        if (values.size() == 1 && values.keySet().iterator().next().contains("/properties/")) {
            return false;
        }

        return values.values()
                .stream()
                .filter(Map.class::isInstance)
                .count() == values.size();
    }

    /**
     * Some Swagger generators will add anyOf/oneOf for the majority of fields, even though there are not 2 types of request bodies.
     *
     * @param xxxOfSchemas the list of xxxOf schemas
     * @return a list with all schemas that don't have a null type
     */
    private List<Schema> excludeNullSchemas(List<Schema> xxxOfSchemas) {
        return Optional.ofNullable(xxxOfSchemas).orElse(Collections.emptyList())
                .stream()
                .filter(schema -> schema.getType() != null || schema.get$ref() != null)
                .toList();
    }

    private void createMergedSchema(String schemaName, List<Schema> allOfSchema) {
        Schema<?> newSchema = new Schema<>();
        newSchema.properties(new LinkedHashMap<>());
        newSchema.required(new ArrayList<>());

        for (Schema<?> schema : allOfSchema) {
            if (schema.get$ref() != null) {
                schema = globalContext.getSchemaFromReference(schema.get$ref());
            }
            newSchema.getProperties().putAll(schema.getProperties() != null ? schema.getProperties() : Collections.emptyMap());
            newSchema.getRequired().addAll(schema.getRequired() != null ? schema.getRequired() : Collections.emptyList());
        }
        globalContext.getSchemaMap().put(CatsModelUtils.getSimpleRef(schemaName), newSchema);
    }

    private void mapDiscriminator(Schema<?> composedSchema, List<Schema> anyOf) {
        if (composedSchema.getDiscriminator() != null) {
            globalContext.getDiscriminators().add(composedSchema.getDiscriminator());
            for (Schema<?> anyOfSchema : anyOf) {
                Discriminator discriminator = new Discriminator();
                discriminator.setPropertyName(composedSchema.getDiscriminator().getPropertyName());
                if (anyOfSchema.get$ref() != null) {
                    globalContext.getSchemaFromReference(anyOfSchema.get$ref()).setDiscriminator(discriminator);
                } else {
                    anyOfSchema.setDiscriminator(discriminator);
                }
            }
        }
    }

    private void addXXXOfExamples(Map<String, Object> values, Object propertyName, Collection<Schema> allOf, String of) {
        Set<String> storedSchemaRefs = new HashSet<>();
        int i = 0;

        for (Schema allOfSchema : allOf) {
            String fullSchemaRef = allOfSchema.get$ref();
            String schemaRef;

            if (CatsModelUtils.isArraySchema(allOfSchema)) {
                fullSchemaRef = allOfSchema.getItems().get$ref();
            }

            Schema schemaToExample = allOfSchema;
            if (fullSchemaRef != null) {
                schemaRef = CatsModelUtils.getSimpleRef(fullSchemaRef);
                schemaToExample = this.globalContext.getSchemaFromReference(fullSchemaRef);
            } else {
                schemaRef = schemaToExample.getType();

                if (storedSchemaRefs.contains(schemaRef)) {
                    schemaRef = schemaRef + ++i;
                }
                fullSchemaRef = "#" + schemaRef;
                storedSchemaRefs.add(schemaRef);
            }
            String propertyKey = propertyName.toString() + "_" + schemaRef;
            String keyToStore = currentProperty.contains("#") ? currentProperty.substring(currentProperty.lastIndexOf("#") + 1) : currentProperty;
            values.put(keyToStore + of + fullSchemaRef, resolveModelToExample(propertyKey, schemaToExample));
        }
    }
}
