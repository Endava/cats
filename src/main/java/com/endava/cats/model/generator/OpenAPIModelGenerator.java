package com.endava.cats.model.generator;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    /**
     * Constructs an OpenAPIModelGenerator with the specified configuration.
     *
     * @param catsGlobalContext   The global context for CATS.
     * @param validDataFormat     The format to use for generating valid data.
     * @param useExamplesArgument Flag indicating whether to use examples from the OpenAPI specification.
     * @param selfReferenceDepth  The maximum depth for generating self-referencing models.
     */
    public OpenAPIModelGenerator(CatsGlobalContext catsGlobalContext, ValidDataFormat validDataFormat, boolean useExamplesArgument, int selfReferenceDepth) {
        this.globalContext = catsGlobalContext;
        this.random = CatsUtil.random();
        this.useExamples = useExamplesArgument;
        this.selfReferenceDepth = selfReferenceDepth;
        this.validDataFormat = validDataFormat;
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
            final Schema schema = this.globalContext.getSchemaMap().get(modelName);
            if (schema != null) {
                String example = Json.pretty(this.resolveModelToExample(modelName, schema));

                if (example != null) {
                    kv.put(EXAMPLE, example);
                    return Map.copyOf(kv);
                }
            }
        }
        return Collections.emptyMap();
    }

    private <T> Object resolvePropertyToExample(String propertyName, Schema<T> propertySchema) {
        String propertyForGeneration = currentProperty.endsWith(propertyName) ? currentProperty : currentProperty + "#" + propertyName;
        Object generatedValueFromFormat = validDataFormat.generate(propertySchema, propertyForGeneration);

        if (generatedValueFromFormat != null && notSyntheticSchema(propertyName) && CatsModelUtils.isStringSchema(propertySchema) && propertySchema.getEnum() == null) {
            return generatedValueFromFormat;
        } else if (propertySchema.getExample() != null && canUseExamples(propertySchema)) {
            logger.trace("Example set in swagger spec, returning example: '{}'", propertySchema.getExample());
            return this.formatExampleIfNeeded(propertySchema);
        } else if (CatsModelUtils.isStringSchema(propertySchema)) {
            return this.getExampleFromStringSchema(propertyName, propertySchema);
        } else if (CatsModelUtils.isBooleanSchema(propertySchema)) {
            return this.getExampleFromBooleanSchema(propertySchema);
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

    private boolean notSyntheticSchema(String currentProperty) {
        return !currentProperty.startsWith(SYNTH_SCHEMA_NAME);
    }

    private <T> Object formatExampleIfNeeded(Schema<T> property) {
        if (CatsModelUtils.isDateSchema(property)) {
            return DATE_FORMATTER.format(LocalDate.ofInstant(((Date) property.getExample()).toInstant(), ZoneId.systemDefault()));
        }
        if (CatsModelUtils.isDateTimeSchema(property)) {
            return ((OffsetDateTime) property.getExample()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (CatsModelUtils.isBinarySchema(property) || CatsModelUtils.isByteArraySchema(property)) {
            return Base64.getDecoder().decode((byte[]) property.getExample());
        }
        return property.getExample();
    }

    private Object resolveProperties(Schema<?> schema) {
        if (schema.getProperties() != null) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
                result.put(property.getKey(), this.resolvePropertyToExample(property.getKey(), property.getValue()));
            }
            return result;
        }
        return schema.getExample();
    }

    private <T> boolean canUseExamples(Schema<T> property) {
        return useExamples || catsGeneratedExamples.contains(property);
    }

    private <T> Object getExampleForObjectSchema(Schema<T> property) {
        if (property.getExample() != null) {
            return property.getExample();
        }
        if (property.getProperties() == null || property.getProperties().isEmpty()) {
            return new HashMap<>();
        }
        return "{\"cats\":\"cats\"}";
    }

    private <T> Object getExampleFromStringSchema(String propertyName, Schema<T> property) {
        logger.trace("String property {}", propertyName);

        List<T> enumValues = property.getEnum();
        if (!CollectionUtils.isEmpty(enumValues)) {
            return enumValues.stream().filter(Objects::nonNull).findFirst().orElse(enumValues.get(0));
        }
        if (property.getMinLength() != null || property.getMaxLength() != null) {
            return generateValueBasedOnMinMax(property);
        }
        if (CatsModelUtils.isDecimalSchema(property)) {
            return generateBigDecimal(property);
        }
        if (property.getPattern() != null) {
            return StringGenerator.generate(property.getPattern(), 1, 2000);
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
        Map<String, Object> mp = new HashMap<>();
        globalContext.getAdditionalProperties().add(propertyName);
        if (property.getName() != null) {
            mp.put(property.getName(), resolvePropertyToExample(propertyName, (Schema) property.getAdditionalProperties()));
        } else if (((Schema) property.getAdditionalProperties()).get$ref() != null) {
            Schema innerSchema = (Schema) property.getAdditionalProperties();
            Schema addPropSchema = this.globalContext.getSchemaMap().get(innerSchema.get$ref().substring(innerSchema.get$ref().lastIndexOf('/') + 1));
            mp.put("key", resolvePropertyToExample(propertyName, addPropSchema));
        } else {
            mp.put("key", resolvePropertyToExample(propertyName, (Schema) property.getAdditionalProperties()));
        }
        return mp;
    }

    private Object getExampleFromIntegerSchema(Schema<?> property) {
        Double min = property.getMinimum() == null ? null : property.getMinimum().doubleValue();
        Double max = property.getMaximum() == null ? null : property.getMaximum().doubleValue();
        if (property.getEnum() != null) {
            return property.getEnum().get(random.nextInt(0, property.getEnum().size()));
        }
        if (property.getDefault() != null) {
            return property.getDefault();
        }
        if (CatsModelUtils.isShortIntegerSchema(property)) {
            return (int) randomNumber(min, max);
        }
        return (long) randomNumber(min, max);
    }

    private Object getExampleFromNumberSchema(Schema<?> property) {
        Double min = property.getMinimum() == null ? null : property.getMinimum().doubleValue();
        Double max = property.getMaximum() == null ? null : property.getMaximum().doubleValue();

        return randomNumber(min, max);
    }

    private Object getExampleFromBooleanSchema(Schema<?> property) {
        Object defaultValue = property.getDefault();
        if (defaultValue != null) {
            return defaultValue;
        }
        return Boolean.TRUE;
    }

    private Object getExampleFromArraySchema(String propertyName, Schema property) {
        Schema innerType = property.getItems();
        if (innerType == null) {
            return "[]";
        }
        if (innerType.get$ref() != null) {
            innerType = this.globalContext.getSchemaMap().get(innerType.get$ref().substring(innerType.get$ref().lastIndexOf('/') + 1));
        }
        if (innerType != null) {
            int arrayLength = null == property.getMinItems() ? 2 : property.getMinItems();
            Object[] objectProperties = new Object[arrayLength];

            for (int i = 0; i < arrayLength; i++) {
                boolean isNullSchema = innerType.getType() == null && innerType.get$ref() == null && !CatsModelUtils.isComposedSchema(innerType) && innerType.getProperties() == null;
                if (isNullSchema) {
                    objectProperties[i] = StringGenerator.generate(StringGenerator.ALPHANUMERIC_PLUS, propertyName.length(), propertyName.length() + 4);
                } else {
                    objectProperties[i] = resolveModelToExample(propertyName, innerType);
                }
            }
            return objectProperties;
        }
        return "[]";
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
        Map<String, Object> values = new HashMap<>();
        logger.trace("Resolving model '{}' to example", name);
        schema = normalizeDiscriminatorMappingsToOneOf(name, schema);

        if (JsonUtils.isCyclicReference(currentProperty, selfReferenceDepth)) {
            return values;
        }

        if (schema.getProperties() != null) {
            logger.trace("Schema properties not null {}: {}", name, schema.getProperties().keySet());
            this.processSchemaProperties(name, schema, values);
        }
        if (CatsModelUtils.isComposedSchema(schema)) {
            this.populateWithComposedSchema(values, name, schema);
        } else {
            globalContext.getRequestDataTypes().put(currentProperty, schema);
            return this.resolvePropertyToExample(name, schema);
        }
        return values;
    }

    private void processSchemaProperties(String name, Schema schema, Map<String, Object> values) {
        logger.trace("Creating example from model values {}", name);

        if (schema.getDiscriminator() != null) {
            globalContext.getDiscriminators().add(schema.getDiscriminator());
        }
        String previousPropertyValue = currentProperty;
        for (Object propertyName : schema.getProperties().keySet()) {
            String schemaRef = ((Schema) schema.getProperties().get(propertyName)).get$ref();
            Schema innerSchema = this.globalContext.getSchemaMap().get(schemaRef != null ? schemaRef.substring(schemaRef.lastIndexOf('/') + 1) : "");
            currentProperty = previousPropertyValue.isEmpty() ? propertyName.toString() : previousPropertyValue + "#" + propertyName.toString();

            if (innerSchema == null) {
                this.parseFromInnerSchema(name, schema, values, propertyName);
            } else if (schema.getDiscriminator() != null && schema.getDiscriminator().getPropertyName().equalsIgnoreCase(propertyName.toString())) {
                values.put(propertyName.toString(), this.matchToEnumOrEmpty(name, innerSchema, propertyName.toString()));
            } else {
                values.put(propertyName.toString(), this.resolveModelToExample(propertyName.toString(), innerSchema));
            }
        }
        currentProperty = previousPropertyValue;
        schema.setExample(values);
        catsGeneratedExamples.add(schema);
    }

    private Schema normalizeDiscriminatorMappingsToOneOf(String name, Schema<?> schema) {
        if (schema.getDiscriminator() != null && !CollectionUtils.isEmpty(schema.getDiscriminator().getMapping())
                && !CatsModelUtils.isComposedSchema(schema) && !isAnyComposedSchemaInChain(name)) {
            Schema<?> composedSchema = new Schema<>();
            composedSchema.setOneOf(schema.getDiscriminator().getMapping().values()
                    .stream().map(schemaName -> new Schema<>().$ref(schemaName))
                    .toList());
            composedSchema.setDiscriminator(schema.getDiscriminator());
            schema.getProperties().get(schema.getDiscriminator().getPropertyName()).setEnum(new ArrayList<>(schema.getDiscriminator().getMapping().keySet()));
            globalContext.getDiscriminators().add(schema.getDiscriminator());
            Schema<?> newSchema = new ObjectMapper().convertValue(schema, Schema.class);
            newSchema.setName("CatsChanged" + name);
            newSchema.getDiscriminator().setMapping(null);
            globalContext.getSchemaMap().put(newSchema.getName(), newSchema);
            for (String oneOfSchema : schema.getDiscriminator().getMapping().values()) {
                oneOfSchema = oneOfSchema.substring(oneOfSchema.lastIndexOf("/") + 1);
                Schema<?> currentOneOfSchema = globalContext.getSchemaMap().get(oneOfSchema);
                currentOneOfSchema.getAllOf()
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
        return Arrays.stream(schemaRefs).anyMatch(entry -> CatsModelUtils.isComposedSchema(globalContext.getSchemaMap().get(entry)));
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
            addXXXOfExamples(values, propertyName, composedSchema.getAllOf(), "ALL_OF");
            String newKey = "ALL_OF";
            Map<String, Object> finalMap = new HashMap<>();
            boolean innerAllOff = false;

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map map) {
                    finalMap.putAll(map);
                } else {
                    innerAllOff = true;
                }
            }

            if (!innerAllOff) {
                values.clear();
                values.put(newKey, finalMap);
            } else {
                values.put(propertyName, finalMap);
            }
            this.createMergedSchema(propertyName, composedSchema.getAllOf());
        }
        List<Schema> anyOfNonNullSchemas = this.excludeNullSchemas(composedSchema.getAnyOf());
        if (anyOfNonNullSchemas.size() == 1) {
            values.put(propertyName, resolveModelToExample(propertyName, anyOfNonNullSchemas.get(0)));
        } else if (composedSchema.getAnyOf() != null) {
            mapDiscriminator(composedSchema, anyOfNonNullSchemas);
            addXXXOfExamples(values, propertyName, anyOfNonNullSchemas, "ANY_OF");
        }

        List<Schema> oneOfNonNullSchemas = this.excludeNullSchemas(composedSchema.getOneOf());
        if (oneOfNonNullSchemas.size() == 1) {
            values.put(propertyName, resolveModelToExample(propertyName, oneOfNonNullSchemas.get(0)));
        } else if (composedSchema.getOneOf() != null) {
            mapDiscriminator(composedSchema, oneOfNonNullSchemas);
            addXXXOfExamples(values, propertyName, oneOfNonNullSchemas, "ONE_OF");
        }
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
                schema = globalContext.getSchemaMap().get(schema.get$ref().substring(schema.get$ref().lastIndexOf("/") + 1));
            }
            newSchema.getProperties().putAll(schema.getProperties() != null ? schema.getProperties() : Collections.emptyMap());
            newSchema.getRequired().addAll(schema.getRequired() != null ? schema.getRequired() : Collections.emptyList());
        }
        globalContext.getSchemaMap().put(schemaName, newSchema);
    }

    private void mapDiscriminator(Schema<?> composedSchema, List<Schema> anyOf) {
        if (composedSchema.getDiscriminator() != null) {
            globalContext.getDiscriminators().add(composedSchema.getDiscriminator());
            for (Schema<?> anyOfSchema : anyOf) {
                Discriminator discriminator = new Discriminator();
                discriminator.setPropertyName(composedSchema.getDiscriminator().getPropertyName());
                if (anyOfSchema.get$ref() != null) {
                    globalContext.getSchemaMap().get(anyOfSchema.get$ref().substring(anyOfSchema.get$ref().lastIndexOf("/") + 1)).setDiscriminator(discriminator);
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
                schemaRef = fullSchemaRef.substring(fullSchemaRef.lastIndexOf('/') + 1);
                schemaToExample = this.globalContext.getSchemaMap().get(schemaRef);
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