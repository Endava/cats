package com.endava.cats.model.generator;

import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * A modified version of @code{io.swagger.codegen.examples.ExampleGenerator} that takes into consideration several other request
 * setups including complex objects and array of objects.
 * <p>
 * This is a stateful object. Don't use it through dependency injection.
 */
public class PayloadGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadGenerator.class);

    private static final String MIME_TYPE_JSON = MimeTypeUtils.APPLICATION_JSON_VALUE;
    private static final String EXAMPLE = "example";
    private static final String CONTENT_TYPE = "contentType";
    private static final String URL = "url";
    private static final String URI = "uri";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Set<Schema<?>> catsGeneratedExamples = new HashSet<>();
    private final Random random;
    private final DecimalFormat df = new DecimalFormat("#.00");
    private final boolean useExamples;
    private final CatsGlobalContext globalContext;
    private String currentProperty = "";

    public PayloadGenerator(CatsGlobalContext catsGlobalContext, boolean useExamplesArgument) {
        this.globalContext = catsGlobalContext;
        this.random = new Random("PayloadGenerator".hashCode());
        useExamples = useExamplesArgument;
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

    public List<Map<String, String>> generate(List<String> mediaTypes, String modelName) {
        List<Map<String, String>> output = new ArrayList<>();

        if (mediaTypes == null) {
            // assume application/json for this
            mediaTypes = Collections.singletonList(MIME_TYPE_JSON);
        }
        for (String mediaType : mediaTypes) {
            Map<String, String> kv = new HashMap<>();
            kv.put(CONTENT_TYPE, mediaType);
            if (modelName != null) {
                final Schema schema = this.globalContext.getSchemaMap().get(modelName);
                if (schema != null) {
                    String example = Json.pretty(this.resolveModelToExample(modelName, mediaType, schema));

                    if (example != null) {
                        kv.put(EXAMPLE, example);
                        output.add(kv);
                    }
                }
            }
        }

        return output;
    }

    private <T> Object resolvePropertyToExample(String propertyName, String mediaType, Schema<T> property) {
        LOGGER.trace("Resolving example for property {}...", property);
        if (property.getExample() != null && canUseExamples(property)) {
            LOGGER.trace("Example set in swagger spec, returning example: '{}'", property.getExample());
            return property.getExample();
        } else if (property instanceof StringSchema) {
            return this.getExampleFromStringSchema(propertyName, (StringSchema) property);
        } else if (property instanceof BooleanSchema) {
            return this.getExampleFromBooleanSchema((BooleanSchema) property);
        } else if (property instanceof ArraySchema) {
            Object objectProperties = this.getExampleFromArraySchema(propertyName, mediaType, property);
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
        } else if (property.getAdditionalProperties() instanceof Schema) {
            return this.getExampleFromAdditionalPropertiesSchema(propertyName, mediaType, property);
        } else if (property instanceof ObjectSchema) {
            return this.getExampleForObjectSchema(property);
        } else if (property instanceof UUIDSchema) {
            return UUID.randomUUID().toString();
        } else if (property instanceof ByteArraySchema) {
            return this.getExampleForByteArraySchema(property);
        }

        return property.getExample();
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
        LOGGER.trace("String property");

        String defaultValue = property.getDefault();
        if (StringUtils.isNotBlank(defaultValue)) {
            LOGGER.trace("Default value found: '{}'", defaultValue);
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
            return "cool.cats@cats.io";
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
        LOGGER.trace("No values found, using property name {} as example", propertyName);
        return StringGenerator.generate(StringGenerator.ALPHANUMERIC_PLUS, propertyName.length(), propertyName.length() + 4);
    }

    boolean isURI(Schema<String> property, String propertyName) {
        return URI.equals(property.getFormat()) || URL.equals(property.getFormat()) || propertyName.equalsIgnoreCase(URL) || propertyName.equalsIgnoreCase(URI)
                || propertyName.toLowerCase(Locale.ROOT).endsWith(URL) || propertyName.toLowerCase(Locale.ROOT).endsWith(URI);
    }

    Map<String, Object> getExampleFromAdditionalPropertiesSchema(String propertyName, String mediaType, Schema property) {
        Map<String, Object> mp = new HashMap<>();
        globalContext.getAdditionalProperties().add(propertyName);
        if (property.getName() != null) {
            mp.put(property.getName(), resolvePropertyToExample(propertyName, mediaType, (Schema) property.getAdditionalProperties()));
        } else if (((Schema) property.getAdditionalProperties()).get$ref() != null) {
            Schema innerSchema = (Schema) property.getAdditionalProperties();
            Schema addPropSchema = this.globalContext.getSchemaMap().get(innerSchema.get$ref().substring(innerSchema.get$ref().lastIndexOf('/') + 1));
            mp.put("key", resolvePropertyToExample(propertyName, mediaType, addPropSchema));
        } else {
            mp.put("key", resolvePropertyToExample(propertyName, mediaType, (Schema) property.getAdditionalProperties()));
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

        return df.format(randomNumber(min, max));
    }

    private Object getExampleFromBooleanSchema(Schema<Boolean> property) {
        Object defaultValue = property.getDefault();
        if (defaultValue != null) {
            return defaultValue;
        }
        return Boolean.TRUE;
    }

    private Object getExampleFromArraySchema(String propertyName, String mediaType, Schema property) {
        Schema innerType = ((ArraySchema) property).getItems();
        if (innerType.get$ref() != null) {
            innerType = this.globalContext.getSchemaMap().get(innerType.get$ref().substring(innerType.get$ref().lastIndexOf('/') + 1));
        }
        if (innerType != null) {
            int arrayLength = null == property.getMaxItems() ? 2 : property.getMaxItems();
            Object[] objectProperties = new Object[arrayLength];
            Object objProperty = resolveModelToExample(propertyName, mediaType, innerType);
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

    private Object resolveModelToExample(String name, String mediaType, Schema schema) {
        Map<String, Object> values = new HashMap<>();

        LOGGER.trace("Resolving model '{}' to example", name);

        if (schema.getProperties() != null) {
            this.processSchemaProperties(name, mediaType, schema, values);
        }
        if (schema instanceof ComposedSchema) {
            this.populateWithComposedSchema(mediaType, values, name, (ComposedSchema) schema);
        } else {
            globalContext.getRequestDataTypes().put(currentProperty, schema);
            return this.resolvePropertyToExample(name, mediaType, schema);
        }
        return values;
    }

    private void processSchemaProperties(String name, String mediaType, Schema schema, Map<String, Object> values) {
        LOGGER.trace("Creating example from model values");
        if (schema.getDiscriminator() != null) {
            globalContext.getDiscriminators().add(currentProperty + "#" + schema.getDiscriminator().getPropertyName());
        }
        String previousPropertyValue = currentProperty;
        for (Object propertyName : schema.getProperties().keySet()) {
            String schemaRef = ((Schema) schema.getProperties().get(propertyName)).get$ref();
            Schema innerSchema = this.globalContext.getSchemaMap().get(schemaRef != null ? schemaRef.substring(schemaRef.lastIndexOf('/') + 1) : "");
            currentProperty = previousPropertyValue.isEmpty() ? propertyName.toString() : previousPropertyValue + "#" + propertyName.toString();

            if (innerSchema == null) {
                this.parseFromInnerSchema(name, mediaType, schema, values, propertyName);
            } else {
                values.put(propertyName.toString(), this.resolveModelToExample(propertyName.toString(), mediaType, innerSchema));
            }
        }
        currentProperty = previousPropertyValue;
        schema.setExample(values);
        catsGeneratedExamples.add(schema);
    }

    private void parseFromInnerSchema(String name, String mediaType, Schema schema, Map<String, Object> values, Object propertyName) {
        Schema innerSchema = (Schema) schema.getProperties().get(propertyName.toString());
        if (innerSchema instanceof ObjectSchema) {
            values.put(propertyName.toString(), resolveModelToExample(propertyName.toString(), mediaType, innerSchema));
        }
        if (innerSchema instanceof ComposedSchema) {
            this.populateWithComposedSchema(mediaType, values, propertyName.toString(), (ComposedSchema) innerSchema);
        } else if (schema.getDiscriminator() != null && schema.getDiscriminator().getPropertyName().equalsIgnoreCase(propertyName.toString())) {
            values.put(propertyName.toString(), innerSchema.getEnum().stream().filter(value -> name.contains(value.toString())).findFirst().orElse(""));
            globalContext.getRequestDataTypes().put(currentProperty, innerSchema);
        } else {
            Object example = this.resolvePropertyToExample(propertyName.toString(), mediaType, innerSchema);
            values.put(propertyName.toString(), example);
            globalContext.getRequestDataTypes().put(currentProperty, innerSchema);
        }
    }

    private void populateWithComposedSchema(String mediaType, Map<String, Object> values, String propertyName, ComposedSchema composedSchema) {
        if (composedSchema.getAllOf() != null) {
            addXXXOfExamples(mediaType, values, propertyName, composedSchema.getAllOf(), "ALL_OF");
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
            addXXXOfExamples(mediaType, values, propertyName, composedSchema.getAnyOf(), "ANY_OF");
        }

        if (composedSchema.getOneOf() != null) {
            addXXXOfExamples(mediaType, values, propertyName, composedSchema.getOneOf(), "ONE_OF");
        }
    }

    private void addXXXOfExamples(String mediaType, Map<String, Object> values, Object propertyName, List<Schema> allOf, String of) {
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
            values.put(propertyName + of + fullSchemaRef, resolveModelToExample(propertyKey, mediaType, schemaToExample));
        }
    }
}