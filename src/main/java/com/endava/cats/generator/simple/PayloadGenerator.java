package com.endava.cats.generator.simple;

import com.mifmif.common.regex.Generex;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * A modified version of @code{io.swagger.codegen.examples.ExampleGenerator} that takes into consideration several other request
 * setups including complex objects and array of objects
 */
public class PayloadGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadGenerator.class);


    private static final String MIME_TYPE_JSON = "application/json";
    private static final String EXAMPLE = "example";
    private static final String CONTENT_TYPE = "contentType";
    private static final String URL = "url";
    private static final String URI = "uri";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /**
     * There is no need to re-compute this for each path, as the request data types are common across all requests
     */
    private static final Map<String, Schema> requestDataTypes = new HashMap<>();
    private final Random random;
    private final Map<String, Schema> schemaMap;
    private final DecimalFormat df = new DecimalFormat("#.00");
    private final List<String> discriminators = new ArrayList<>();
    private String currentProperty = "";

    public PayloadGenerator(Map<String, Schema> schemas) {
        // use a fixed seed to make the "random" numbers reproducible.
        this.random = new Random("PayloadGenerator".hashCode());
        this.schemaMap = schemas;
    }

    public static Map<String, Schema> getRequestDataTypes() {
        return requestDataTypes;
    }

    public List<String> getDiscriminators() {
        return this.discriminators;
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
            if (modelName != null && mediaType.startsWith(MIME_TYPE_JSON)) {
                final Schema schema = this.schemaMap.get(modelName);
                if (schema != null) {
                    String example = Json.pretty(resolveModelToExample(modelName, mediaType, schema));

                    if (example != null) {
                        kv.put(EXAMPLE, example);
                        output.add(kv);
                    }
                }
            }
        }

        return output;
    }

    private Object resolvePropertyToExample(String propertyName, String mediaType, Schema property) {
        LOGGER.debug("Resolving example for property {}...", property);
        if (property.getExample() != null) {
            LOGGER.debug("Example set in swagger spec, returning example: '{}'", property.getExample());
            return property.getExample();
        } else if (property instanceof StringSchema || "string".equalsIgnoreCase(property.getType())) {
            LOGGER.debug("String property");

            String defaultValue = (String) property.getDefault();
            if (defaultValue != null && !defaultValue.isEmpty()) {
                LOGGER.debug("Default value found: '{}'", defaultValue);
                return defaultValue;
            }
            List<String> enumValues = property.getEnum();
            if (enumValues != null && !enumValues.isEmpty()) {
                LOGGER.debug("Enum value found: '{}'", enumValues.get(0));
                return enumValues.get(0);
            }
            String format = property.getFormat();
            if (URI.equals(format) || URL.equals(format)) {
                LOGGER.debug("URI or URL format, without default or enum, generating random one.");
                return "http://example.com/aeiou";
            }
            if (isEmailAddress(propertyName)) {
                return "cool.cats@cats.io";
            }
            if (isIP(propertyName)) {
                return "10.10.10.20";
            }
            if (property.getMinLength() != null || property.getMaxLength() != null) {
                int minLength = property.getMinLength() != null ? property.getMinLength() : 0;
                int maxLength = property.getMaxLength() != null ? property.getMaxLength() - 1 : 10;
                String pattern = property.getPattern() != null ? property.getPattern() : "[a-zA-Z0-9]*";
                if (maxLength < minLength) {
                    maxLength = minLength;
                }
                return new Generex(StringGenerator.sanitize(pattern)).random(minLength, maxLength);
            }
            if (property.getPattern() != null) {
                return new Generex(StringGenerator.sanitize(property.getPattern())).random();
            }
            LOGGER.debug("No values found, using property name {} as example", propertyName);
            return propertyName;
        } else if (property instanceof BooleanSchema || "boolean".equalsIgnoreCase(property.getType())) {
            Object defaultValue = property.getDefault();
            if (defaultValue != null) {
                return defaultValue;
            }
            return Boolean.TRUE;
        } else if (property instanceof ArraySchema) {
            Schema innerType = ((ArraySchema) property).getItems();
            if (innerType.get$ref() != null) {
                innerType = this.schemaMap.get(innerType.get$ref().substring(innerType.get$ref().lastIndexOf('/') + 1));
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
        } else if (property instanceof DateSchema) {
            return DATE_FORMATTER.format(LocalDateTime.now());
        } else if (property instanceof DateTimeSchema) {
            return ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else if (property instanceof NumberSchema || "number".equalsIgnoreCase(property.getType())) {
            Double min = property.getMinimum() == null ? null : property.getMinimum().doubleValue();
            Double max = property.getMaximum() == null ? null : property.getMaximum().doubleValue();
            if (SchemaTypeUtil.FLOAT_FORMAT.equals(property.getFormat())) {
                return df.format(randomNumber(min, max));
            }
            return df.format(randomNumber(min, max));
        } else if (property instanceof FileSchema) {
            return "";
        } else if (property instanceof IntegerSchema || "integer".equalsIgnoreCase(property.getType())) {
            Double min = property.getMinimum() == null ? null : property.getMinimum().doubleValue();
            Double max = property.getMaximum() == null ? null : property.getMaximum().doubleValue();
            if (SchemaTypeUtil.INTEGER32_FORMAT.equals(property.getFormat())) {
                return (long) randomNumber(min, max);
            }
            return (int) randomNumber(min, max);
        } else if (property.getAdditionalProperties() instanceof Schema) {
            Map<String, Object> mp = new HashMap<>();
            if (property.getName() != null) {
                mp.put(property.getName(),
                        resolvePropertyToExample(propertyName, mediaType, (Schema) property.getAdditionalProperties()));
            } else {
                mp.put("key",
                        resolvePropertyToExample(propertyName, mediaType, (Schema) property.getAdditionalProperties()));
            }
            return mp;
        } else if (property instanceof ObjectSchema) {
            return "{\"cats\":\"cats\"}";
        } else if (property instanceof UUIDSchema) {
            return "046b6c7f-0b8a-43b9-b35d-6489e6daee91";
        }

        return "";
    }

    private boolean isEmailAddress(String property) {
        return property != null && (property.toLowerCase().endsWith("email") || property.toLowerCase().endsWith("emailaddress"));
    }

    private boolean isIP(String property) {
        return property != null && (property.toLowerCase().endsWith("ip") || property.toLowerCase().endsWith("ipaddress"));
    }

    private double randomNumber(Double min, Double max) {
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

        LOGGER.debug("Resolving model '{}' to example", name);

        if (schema.getProperties() != null) {
            LOGGER.debug("Creating example from model values");
            if (schema.getDiscriminator() != null) {
                discriminators.add(currentProperty + "#" + schema.getDiscriminator().getPropertyName());
            }
            String previousPropertyValue = currentProperty;
            for (Object propertyName : schema.getProperties().keySet()) {
                String schemaRef = ((Schema) schema.getProperties().get(propertyName)).get$ref();
                Schema innerSchema = schemaMap.get(schemaRef != null ? schemaRef.substring(schemaRef.lastIndexOf('/') + 1) : "");
                currentProperty = previousPropertyValue.isEmpty() ? propertyName.toString() : previousPropertyValue + "#" + propertyName.toString();

                if (innerSchema == null) {
                    innerSchema = (Schema) schema.getProperties().get(propertyName.toString());
                    if (innerSchema instanceof ObjectSchema) {
                        values.put(propertyName.toString(), resolveModelToExample(propertyName.toString(), mediaType, innerSchema));
                    }
                    if (innerSchema instanceof ComposedSchema) {
                        this.populateWithComposedSchema(mediaType, values, propertyName.toString(), (ComposedSchema) innerSchema);
                    } else if (schema.getDiscriminator() != null && schema.getDiscriminator().getPropertyName().equalsIgnoreCase(propertyName.toString())) {
                        values.put(propertyName.toString(), innerSchema.getEnum().stream().filter(value -> name.contains(value.toString())).findFirst().orElse(""));
                        requestDataTypes.put(currentProperty, innerSchema);
                    } else {
                        Object example = this.resolvePropertyToExample(propertyName.toString(), mediaType, innerSchema);
                        values.put(propertyName.toString(), example);
                        requestDataTypes.put(currentProperty, innerSchema);
                    }
                } else {
                    values.put(propertyName.toString(), resolveModelToExample(propertyName.toString(), mediaType, innerSchema));
                }
            }
            currentProperty = previousPropertyValue;
            schema.setExample(values);
        }
        if (schema instanceof ComposedSchema) {
            this.populateWithComposedSchema(mediaType, values, name, (ComposedSchema) schema);
        } else {
            requestDataTypes.put(currentProperty, schema);
            return this.resolvePropertyToExample(name, mediaType, schema);
        }
        return values;
    }

    private void populateWithComposedSchema(String mediaType, Map<String, Object> values, String propertyName, ComposedSchema composedSchema) {
        if (composedSchema.getAllOf() != null) {
            addXXXOfExamples(mediaType, values, propertyName, composedSchema.getAllOf(), "ALL_OF");
            String newKey = "ALL_OF";
            Map<String, Object> finalMap = new HashMap<>();

            values.forEach((key, value) -> finalMap.putAll((Map) value));
            values.clear();
            values.put(newKey, finalMap);
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
            String schemaRef = allOfSchema.get$ref();
            Schema schemaToExample = allOfSchema;
            if (allOfSchema.get$ref() != null) {
                schemaRef = schemaRef.substring(schemaRef.lastIndexOf('/') + 1);
                schemaToExample = schemaMap.get(schemaRef);
            }
            String propertyKey = propertyName.toString() + (schemaRef == null ? "object" : schemaRef);
            values.put(propertyName.toString() + of + allOfSchema.get$ref(), resolveModelToExample(propertyKey, mediaType, schemaToExample));
        }
    }
}