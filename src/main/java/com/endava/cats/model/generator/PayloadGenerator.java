package com.endava.cats.model.generator;

import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.EmailSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * A modified version of @code{io.swagger.codegen.examples.ExampleGenerator} that takes into consideration several other request
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
public class PayloadGenerator {
    private static final String EXAMPLE = "example";
    private static final String URL = "url";
    private static final String URI = "uri";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(PayloadGenerator.class);
    private final Set<Schema<?>> catsGeneratedExamples = new HashSet<>();
    private final Random random;
    private final boolean useExamples;
    private final CatsGlobalContext globalContext;
    private final int selfReferenceDepth;
    private String currentProperty = "";

    public PayloadGenerator(CatsGlobalContext catsGlobalContext, boolean useExamplesArgument, int selfReferenceDepth) {
        this.globalContext = catsGlobalContext;
        this.random = ThreadLocalRandom.current();
        useExamples = useExamplesArgument;
        this.selfReferenceDepth = selfReferenceDepth;
    }

    public static String generateValueBasedOnMinMAx(Schema<?> property) {
        if (!CollectionUtils.isEmpty(property.getEnum())) {
            return String.valueOf(property.getEnum().get(0));
        }
        int minLength = property.getMinLength() != null ? property.getMinLength() : 5;
        int maxLength = property.getMaxLength() != null ? property.getMaxLength() - 1 : 10;
        String pattern = property.getPattern() != null ? property.getPattern() : StringGenerator.ALPHANUMERIC_PLUS;
        if (maxLength < minLength) {
            maxLength = minLength;
        }
        return StringGenerator.generate(pattern, minLength, maxLength);
    }

    public static FuzzingStrategy getFuzzStrategyWithRepeatedCharacterReplacingValidValue(FuzzingData data, String fuzzedField, String characterToRepeat) {
        Schema<?> schema = data.getRequestPropertyTypes().get(fuzzedField);
        String spaceValue = characterToRepeat;
        if (schema != null && schema.getMinLength() != null) {
            spaceValue = StringUtils.repeat(spaceValue, (schema.getMinLength() / spaceValue.length()) + 1);
        }
        return FuzzingStrategy.replace().withData(spaceValue);
    }

    public Map<String, String> generate(String modelName) {
        Map<String, String> kv = new HashMap<>();
        if (modelName != null) {
            final Schema schema = this.globalContext.getSchemaMap().get(modelName);
            if (schema != null) {
                String example = Json.pretty(this.resolveModelToExample(modelName, schema));

                if (example != null) {
                    kv.put(EXAMPLE, example);
                    return kv;
                }
            }
        }


        return Collections.emptyMap();
    }

    private <T> Object resolvePropertyToExample(String propertyName, Schema<T> property) {
        if (property.getExample() != null && canUseExamples(property)) {
            logger.trace("Example set in swagger spec, returning example: '{}'", property.getExample());
            return property.getExample();
        } else if (property instanceof StringSchema) {
            return this.getExampleFromStringSchema(propertyName, (StringSchema) property);
        } else if (property instanceof BooleanSchema) {
            return this.getExampleFromBooleanSchema((BooleanSchema) property);
        } else if (property instanceof ArraySchema) {
            Object objectProperties = this.getExampleFromArraySchema(propertyName, property);
            if (objectProperties != null) {
                return objectProperties;
            }
        } else if (property instanceof DateSchema) {
            return DATE_FORMATTER.format(LocalDateTime.now());
        } else if (property instanceof DateTimeSchema) {
            return ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else if (property instanceof NumberSchema) {
            return this.getExampleFromNumberSchema((NumberSchema) property);
        } else if (property instanceof IntegerSchema) {
            return this.getExampleFromIntegerSchema((IntegerSchema) property);
        } else if (property instanceof ObjectSchema) {
            return this.getExampleForObjectSchema(property);
        } else if (property instanceof UUIDSchema) {
            return UUID.randomUUID().toString();
        } else if (property instanceof ByteArraySchema) {
            return this.getExampleForByteArraySchema(property);
        } else if (property instanceof EmailSchema) {
            return "cool.cats@cats.io";
        } else if (property.getAdditionalProperties() instanceof Schema) {
            return this.getExampleFromAdditionalPropertiesSchema(propertyName, property);
        }

        return resolveProperties(property);
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

    private <T> Object getExampleForByteArraySchema(Schema<T> property) {
        String value = generateValueBasedOnMinMAx(property);
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private <T> Object getExampleForObjectSchema(Schema<T> property) {
        return property.getExample() != null ? property.getExample() : "{\"cats\":\"cats\"}";
    }

    private Object getExampleFromStringSchema(String propertyName, Schema<String> property) {
        logger.trace("String property {}", propertyName);

        String defaultValue = property.getDefault();
        if (StringUtils.isNotBlank(defaultValue)) {
            logger.trace("Default value found: '{}'", defaultValue);
            return defaultValue;
        }
        List<String> enumValues = property.getEnum();
        if (!CollectionUtils.isEmpty(enumValues)) {
            return enumValues.get(0);
        }
        if (isURI(property, propertyName)) {
            return "http://example.com/aeiou";
        }
        if (isEmailAddress(property, propertyName)) {
            return RandomStringUtils.randomAlphabetic(5) + "cool.cats@cats.io";
        }
        if (isIPV4(property, propertyName)) {
            return "10.10.10.20";
        }
        if (isIPV6(property, propertyName)) {
            return "21DA:D3:0:2F3B:2AA:FF:FE28:9C5A";
        }

        if (property.getMinLength() != null || property.getMaxLength() != null) {
            return generateValueBasedOnMinMAx(property);
        }
        if (property.getPattern() != null) {
            return StringGenerator.generate(property.getPattern(), 1, 2000);
        }
        logger.trace("No values found, using property name {} as example", propertyName);
        return StringGenerator.generate(StringGenerator.ALPHANUMERIC_PLUS, propertyName.length(), propertyName.length() + 4);
    }

    boolean isURI(Schema<String> property, String propertyName) {
        return URI.equals(property.getFormat()) || URL.equals(property.getFormat()) || propertyName.equalsIgnoreCase(URL) || propertyName.equalsIgnoreCase(URI) || propertyName.toLowerCase(Locale.ROOT).endsWith(URL) || propertyName.toLowerCase(Locale.ROOT).endsWith(URI);
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

    private Object getExampleFromIntegerSchema(Schema<Number> property) {
        Double min = property.getMinimum() == null ? null : property.getMinimum().doubleValue();
        Double max = property.getMaximum() == null ? null : property.getMaximum().doubleValue();
        if (SchemaTypeUtil.INTEGER32_FORMAT.equals(property.getFormat())) {
            return (long) randomNumber(min, max);
        }
        return (int) randomNumber(min, max);
    }

    private Object getExampleFromNumberSchema(Schema<BigDecimal> property) {
        Double min = property.getMinimum() == null ? null : property.getMinimum().doubleValue();
        Double max = property.getMaximum() == null ? null : property.getMaximum().doubleValue();

        return randomNumber(min, max);
    }

    private Object getExampleFromBooleanSchema(Schema<Boolean> property) {
        Object defaultValue = property.getDefault();
        if (defaultValue != null) {
            return defaultValue;
        }
        return Boolean.TRUE;
    }

    private Object getExampleFromArraySchema(String propertyName, Schema property) {
        Schema innerType = ((ArraySchema) property).getItems();
        if (innerType.get$ref() != null) {
            innerType = this.globalContext.getSchemaMap().get(innerType.get$ref().substring(innerType.get$ref().lastIndexOf('/') + 1));
        }
        if (innerType != null) {
            int arrayLength = null == property.getMaxItems() ? 2 : property.getMaxItems();
            Object[] objectProperties = new Object[arrayLength];
            Object objProperty = resolveModelToExample(propertyName, innerType);
            for (int i = 0; i < arrayLength; i++) {
                objectProperties[i] = objProperty;
            }
            return objectProperties;
        }
        return null;
    }

    boolean isEmailAddress(Schema<String> property, String propertyName) {
        return propertyName.toLowerCase().endsWith("email") || propertyName.toLowerCase().endsWith("emailaddress") || "email".equalsIgnoreCase(property.getFormat());
    }

    boolean isIPV4(Schema<String> property, String propertyName) {
        return propertyName.toLowerCase().endsWith("ip") || propertyName.toLowerCase().endsWith("ipaddress") || "ip".equalsIgnoreCase(property.getFormat()) || "ipv4".equalsIgnoreCase(property.getFormat());
    }

    boolean isIPV6(Schema<String> property, String propertyName) {
        return propertyName.toLowerCase().endsWith("ipv6") || "ipv6".equalsIgnoreCase(property.getFormat());
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

        if (CatsUtil.isCyclicReference(currentProperty, selfReferenceDepth)) {
            return values;
        }

        if (schema.getProperties() != null) {
            logger.trace("Schema properties not null {}: {}", name, schema.getProperties().keySet());
            this.processSchemaProperties(name, schema, values);
        }
        if (schema instanceof ComposedSchema) {
            this.populateWithComposedSchema(values, name, (ComposedSchema) schema);
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
            } else {
                values.put(propertyName.toString(), this.resolveModelToExample(propertyName.toString(), innerSchema));
            }
        }
        currentProperty = previousPropertyValue;
        schema.setExample(values);
        catsGeneratedExamples.add(schema);
    }

    private Schema normalizeDiscriminatorMappingsToOneOf(String name, Schema<?> schema) {
        if (schema.getDiscriminator() != null && !CollectionUtils.isEmpty(schema.getDiscriminator().getMapping()) && !(schema instanceof ComposedSchema)) {
            ComposedSchema composedSchema = new ComposedSchema();
            composedSchema.setOneOf(schema.getDiscriminator().getMapping().values()
                    .stream().map(schemaName -> new Schema<>().$ref(schemaName))
                    .collect(Collectors.toList()));
            composedSchema.setDiscriminator(schema.getDiscriminator());
            schema.getProperties().get(schema.getDiscriminator().getPropertyName()).setEnum(new ArrayList<>(schema.getDiscriminator().getMapping().keySet()));
            globalContext.getDiscriminators().add(schema.getDiscriminator());
            Schema<?> newSchema = new ObjectMapper().convertValue(schema, Schema.class);
            newSchema.setName("CatsChanged" + name);
            newSchema.getDiscriminator().setMapping(null);
            globalContext.getSchemaMap().put(newSchema.getName(), newSchema);
            for (String oneOfSchema : schema.getDiscriminator().getMapping().keySet()) {
                ComposedSchema currentOneOfSchema = (ComposedSchema) globalContext.getSchemaMap().get(oneOfSchema);
                currentOneOfSchema.getAllOf()
                        .stream()
                        .filter(innerAllOfSchema -> innerAllOfSchema.get$ref() != null)
                        .forEach(innerAllOfSchema -> innerAllOfSchema.set$ref(newSchema.getName()));
            }
            return composedSchema;
        }
        return schema;
    }

    private void parseFromInnerSchema(String name, Schema schema, Map<String, Object> values, Object propertyName) {
        Schema innerSchema = (Schema) schema.getProperties().get(propertyName.toString());
        if (innerSchema instanceof ObjectSchema) {
            values.put(propertyName.toString(), resolveModelToExample(propertyName.toString(), innerSchema));
        }
        if (innerSchema instanceof ComposedSchema) {
            this.populateWithComposedSchema(values, propertyName.toString(), (ComposedSchema) innerSchema);
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

    private void populateWithComposedSchema(Map<String, Object> values, String propertyName, ComposedSchema composedSchema) {
        if (composedSchema.getAllOf() != null) {
            addXXXOfExamples(values, propertyName, composedSchema.getAllOf(), "ALL_OF");
            String newKey = "ALL_OF";
            Map<String, Object> finalMap = new HashMap<>();
            boolean innerAllOff = false;

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    finalMap.putAll((Map) value);
                } else {
                    values.put(key, value);
                    innerAllOff = true;
                }
            }

            if (!innerAllOff) {
                values.clear();
                values.put(newKey, finalMap);
            } else {
                values.put(propertyName, finalMap);
            }
        }

        if (composedSchema.getAnyOf() != null) {
            mapDiscriminator(propertyName, composedSchema, composedSchema.getAnyOf());
            addXXXOfExamples(values, propertyName, composedSchema.getAnyOf(), "ANY_OF");
        }

        if (composedSchema.getOneOf() != null) {
            mapDiscriminator(propertyName, composedSchema, composedSchema.getOneOf());
            addXXXOfExamples(values, propertyName, composedSchema.getOneOf(), "ONE_OF");
        }
    }

    private void mapDiscriminator(String propertyName, ComposedSchema composedSchema, List<Schema> anyOf) {
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
        for (Schema allOfSchema : allOf) {
            String fullSchemaRef = allOfSchema.get$ref();
            String schemaRef;
            if (allOfSchema instanceof ArraySchema) {
                fullSchemaRef = ((ArraySchema) allOfSchema).getItems().get$ref();
            }
            Schema schemaToExample = allOfSchema;
            if (fullSchemaRef != null) {
                schemaRef = fullSchemaRef.substring(fullSchemaRef.lastIndexOf('/') + 1);
                schemaToExample = this.globalContext.getSchemaMap().get(schemaRef);
            } else {
                schemaRef = StringGenerator.generate("[A-Z]{5,10}", 5, 10);
                fullSchemaRef = "#" + schemaRef;
            }
            String propertyKey = propertyName.toString() + schemaRef;
            values.put(propertyName + of + fullSchemaRef, resolveModelToExample(propertyKey, schemaToExample));
        }
    }
}