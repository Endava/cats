package com.endava.cats.util;

import com.endava.cats.util.external.ModelUtils;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Wrapper on top of {@link com.endava.cats.util.external.ModelUtils} in order to accommodate
 * some particular conditions needed by CATS.
 */
@RegisterForReflection
public abstract class CatsModelUtils {
    public static final String X_CATS_FIELD_NAME = "x-cats-field-name";
    private static final Pattern GENERATED_PREFIX = Pattern.compile("generated_.*");
    private static final Pattern GENERATED_BODY_OBJECTS_1 = Pattern.compile("body_\\d*");
    private static final Pattern GENERATED_BODY_OBJECTS_2 = Pattern.compile("^\\w{1,30}_body");
    private static final Pattern GENERATED_BODY_OBJECTS_3 = Pattern.compile("^\\w{1,30}_body_\\d{1,3}$");
    private static final Pattern INLINE_BODY_OBJECTS = Pattern.compile("^inline_response(?:_\\w{0,30}){1,50}$");
    private static final Pattern CROSS_PATHS_REFERENCES_PATTERN = Pattern.compile("^#/paths/.*");
    private static final List<Pattern> PATTERNS_TO_IGNORE = List.of(GENERATED_PREFIX, GENERATED_BODY_OBJECTS_1, GENERATED_BODY_OBJECTS_2, GENERATED_BODY_OBJECTS_3,
            INLINE_BODY_OBJECTS, CROSS_PATHS_REFERENCES_PATTERN);

    private CatsModelUtils() {
        //ntd
    }

    public static boolean isPrimitiveSchema(Schema<?> schema) {
        return isStringSchema(schema) || isNumberSchema(schema) || isBooleanSchema(schema) || isIntegerSchema(schema);
    }

    public static boolean isStringSchema(Schema<?> schema) {
        return schema instanceof StringSchema || isType(schema, SchemaTypeUtil.STRING_TYPE);
    }

    public static boolean isEnumSchema(Schema<?> schema) {
        return ModelUtils.isEnumSchema(schema);
    }

    public static boolean isBooleanSchema(Schema<?> schema) {
        return schema instanceof BooleanSchema || isType(schema, SchemaTypeUtil.BOOLEAN_TYPE);
    }

    public static boolean isArraySchema(Schema<?> schema) {
        if (schema == null) {
            return false;
        } else {
            return schema instanceof ArraySchema || isType(schema, "array");
        }
    }

    public static boolean isNumberSchema(Schema<?> schema) {
        return schema instanceof NumberSchema || isType(schema, SchemaTypeUtil.NUMBER_TYPE);
    }

    public static boolean isFloatSchema(Schema<?> schema) {
        return SchemaTypeUtil.FLOAT_FORMAT.equalsIgnoreCase(schema.getFormat());
    }

    public static boolean isDecimalSchema(Schema<?> schema) {
        return SchemaTypeUtil.NUMBER_TYPE.equalsIgnoreCase(schema.getFormat());
    }

    public static boolean isShortIntegerSchema(Schema<?> schema) {
        return SchemaTypeUtil.INTEGER32_FORMAT.equalsIgnoreCase(schema.getFormat());
    }

    public static boolean isIntegerSchema(Schema<?> schema) {
        return schema instanceof IntegerSchema || isType(schema, SchemaTypeUtil.INTEGER_TYPE);
    }

    public static boolean isDateSchema(Schema<?> schema) {
        return (schema instanceof DateSchema || isType(schema, SchemaTypeUtil.STRING_TYPE)) && SchemaTypeUtil.DATE_FORMAT.equals(schema.getFormat());
    }

    public static boolean isUriSchema(Schema<?> schema) {
        return isStringSchema(schema) && "uri".equals(schema.getFormat());
    }

    public static boolean isUrlSchema(Schema<?> schema) {
        return isStringSchema(schema) && "url".equals(schema.getFormat());
    }

    public static boolean isUUIDSchema(Schema<?> schema) {
        return (schema instanceof UUIDSchema || isType(schema, SchemaTypeUtil.STRING_TYPE)) && SchemaTypeUtil.UUID_FORMAT.equals(schema.getFormat());
    }

    public static boolean isDateTimeSchema(Schema<?> schema) {
        return (schema instanceof DateTimeSchema || isType(schema, SchemaTypeUtil.STRING_TYPE)) && SchemaTypeUtil.DATE_TIME_FORMAT.equals(schema.getFormat());
    }

    public static boolean isBinarySchema(Schema<?> schema) {
        return (schema instanceof BinarySchema || isType(schema, SchemaTypeUtil.STRING_TYPE)) && SchemaTypeUtil.BINARY_FORMAT.equals(schema.getFormat());
    }

    public static boolean isByteArraySchema(Schema<?> schema) {
        return (schema instanceof ByteArraySchema || isType(schema, SchemaTypeUtil.STRING_TYPE)) && SchemaTypeUtil.BYTE_FORMAT.equals(schema.getFormat());
    }


    /**
     * Encapsulating the test for easier maintainability.
     *
     * @param schema the schema being tested
     * @return true if a composed schema, false otherwise
     */
    public static boolean isComposedSchema(Schema<?> schema) {
        return ModelUtils.isComposedSchema(schema);
    }

    /**
     * Composed schemas and Map schemas are not considered ObjectSchemas.
     *
     * @param schema the schema being tested
     * @return true if ObjectSchema, false otherwise
     */
    public static boolean isObjectSchema(Schema<?> schema) {
        return schema instanceof ObjectSchema ||
                (isType(schema, SchemaTypeUtil.OBJECT_TYPE) && !(schema instanceof MapSchema) && !isComposedSchema(schema));
    }

    /**
     * Encapsulating the test for easier maintainability.
     *
     * @param schema the schema being tested
     * @return true if a composed schema, false otherwise
     */
    public static boolean isType(Schema<?> schema, String typeToTest) {
        if (schema == null) {
            return false;
        }
        if (schema instanceof JsonSchema jsonSchema) {
            return Optional.ofNullable(jsonSchema.getTypes())
                    .orElse(Set.of())
                    .stream()
                    .anyMatch(type -> type.equalsIgnoreCase(typeToTest));
        }

        return typeToTest.equalsIgnoreCase(schema.getType());
    }

    /**
     * Gets ref definition name based on full reference.
     *
     * @param ref the full reference
     * @return the simple reference name
     */
    public static String getSimpleRef(String ref) {
        if (ref == null) {
            return null;
        }
        if (!ref.contains("/") || ref.startsWith("#/paths")) {
            return ref;
        }
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    public static String getSimpleRefUsingOAT(String ref) {
        return ModelUtils.getSimpleRef(ref);
    }

    public static boolean isObjectSchemaUsingOAT(Schema<?> schema) {
        return ModelUtils.isObjectSchema(schema);
    }

    /**
     * Returns all schema which don't seem to be used.
     *
     * @param openAPI the OpenAPI Spec
     * @return a list of unused schemas
     */
    public static List<String> getUnusedSchemas(OpenAPI openAPI) {
        return ModelUtils.getUnusedSchemas(openAPI);
    }

    /**
     * Creates an empty ObjectSchema
     *
     * @return a new ObjectSchema
     */
    public static ObjectSchema newObjectSchema() {
        return new ObjectSchema();
    }

    /**
     * Checks if the field name contains a complex regex. For now, this is used to determine if the field is an email or a URI.
     * This is of course not 100% accurate, but it's a good start.
     *
     * @param schema the schema to be checked
     * @return true if the field name contains a complex regex, false otherwise
     */
    public static boolean isComplexRegex(Schema<?> schema) {
        if (StringUtils.isBlank(schema.getPattern())) {
            return false;
        }

        String lowerField = Optional.ofNullable(schema.getExtensions()).orElse(Collections.emptyMap()).getOrDefault(X_CATS_FIELD_NAME, "").toString().toLowerCase(Locale.ROOT);
        String pattern = schema.getPattern();
        return isEmail(pattern, lowerField) || isUri(pattern, lowerField) || isPassword(pattern, lowerField);
    }

    /**
     * Checks if the given combination or pattern and field name is a URI.
     *
     * @param pattern    the pattern of the field
     * @param lowerField the name of the field
     * @return true if the field name contains a complex regex, false otherwise
     */
    public static boolean isUri(String pattern, String lowerField) {
        return (lowerField.endsWith("url") || lowerField.endsWith("uri") || lowerField.endsWith("link")) && ("http://www.test.com".matches(pattern) || "https://www.test.com".matches(pattern));
    }

    /**
     * Checks if the given combination or pattern and field name is an email.
     *
     * @param pattern    the pattern of the field
     * @param lowerField the name of the field
     * @return true if the field name contains a complex regex, false otherwise
     */
    public static boolean isEmail(String pattern, String lowerField) {
        return lowerField.endsWith("email") && "test@test.com".matches(pattern);
    }

    /**
     * Checks if the given combination or pattern and field name is a password.
     *
     * @param pattern    the pattern of the field
     * @param lowerField the name of the field
     * @return true if the field name contains a complex regex, false otherwise
     */
    public static boolean isPassword(String pattern, String lowerField) {
        return lowerField.endsWith("password") && "catsISc00l?!useIt#".matches(pattern);
    }

    /**
     * Checks if a schema is empty. An empty schema is a schema that has no properties,
     * no required fields, no type, no ref, no items, no additionalProperties.
     *
     * @param schema the schema to be checked
     * @return true if the schema is empty, false otherwise
     */
    public static boolean isNotEmptySchema(Schema schema) {
        return schema != null &&
                (StringUtils.isNotBlank(schema.get$ref()) ||
                        StringUtils.isNotBlank(schema.getType()) ||
                        !CatsUtil.isEmpty(schema.getTypes()) ||
                        !CatsUtil.isEmpty(schema.getProperties()) ||
                        !CatsUtil.isEmpty(schema.getAllOf()) ||
                        !CatsUtil.isEmpty(schema.getAnyOf()) ||
                        !CatsUtil.isEmpty(schema.getOneOf()) ||
                        schema.getItems() != null ||
                        !CatsUtil.isEmpty(schema.getRequired()));
    }

    /**
     * Checks if the schema is empty. An empty schema is an Object schema that has no properties,
     * no required fields, no type, no ref, no items, no additionalProperties.
     *
     * @param schema the schema to be checked
     * @return true if the schema is empty, false otherwise
     */
    public static boolean isEmptyObjectSchema(Schema schema) {
        boolean isObject = "object".equalsIgnoreCase(schema.getType()) || (schema.getType() == null && schema.getProperties() != null);
        boolean hasProps = schema.getProperties() != null && !schema.getProperties().isEmpty();
        boolean hasRef = schema.get$ref() != null;
        boolean hasComposition = (schema.getAllOf() != null && !schema.getAllOf().isEmpty())
                || (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty())
                || (schema.getOneOf() != null && !schema.getOneOf().isEmpty());

        return isObject && !hasProps && !hasRef && !hasComposition;
    }

    /**
     * Checks if the schema has a length of 2.
     *
     * @param schema the schema to be checked
     * @return true if the schema has a length of 2, false otherwise
     */
    public static boolean hasLengthTwo(Schema<?> schema) {
        return (schema.getMinLength() != null && schema.getMinLength() == 2) ||
                (schema.getMaxLength() != null && schema.getMaxLength() == 2);
    }

    /**
     * Checks if the schema has a length of 3.
     *
     * @param schema the schema to be checked
     * @return true if the schema has a length of 3, false otherwise
     */
    public static boolean hasLengthThree(Schema<?> schema) {
        return (schema.getMinLength() != null && schema.getMinLength() == 3) ||
                (schema.getMaxLength() != null && schema.getMaxLength() == 3);
    }

    public static boolean isAnyOf(Schema schema) {
        return ModelUtils.isAnyOf(schema);
    }

    public static boolean isOneOf(Schema schema) {
        return ModelUtils.isOneOf(schema);
    }

    public static boolean hasOneOf(Schema schema) {
        return ModelUtils.hasOneOf(schema);
    }

    public static boolean hasAnyOf(Schema schema) {
        return ModelUtils.hasAnyOf(schema);
    }

    public static boolean isAllOf(Schema schema) {
        return ModelUtils.isAllOf(schema);
    }

    public static boolean isAllOfWithProperties(Schema schema) {
        return ModelUtils.isAllOfWithProperties(schema);
    }

    public static List<Schema> getInterfaces(Schema schema) {
        return ModelUtils.getInterfaces(schema);
    }

    public static Schema getSchemaItems(Schema schema) {
        return ModelUtils.getSchemaItems(schema);
    }

    public static boolean isMapSchema(Schema<?> schema) {
        return ModelUtils.isMapSchema(schema);
    }

    public static Schema getAdditionalProperties(Schema schema) {
        return ModelUtils.getAdditionalProperties(schema);
    }

    public static boolean isNotGeneratedPattern(String name) {
        return PATTERNS_TO_IGNORE.stream().noneMatch(pattern -> pattern.matcher(name).matches());
    }
}
