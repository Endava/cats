package com.endava.cats.util;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.openapitools.codegen.utils.ModelUtils;

import java.util.List;
import java.util.stream.Stream;

/**
 * Wrapper on top of {@link org.openapitools.codegen.utils.ModelUtils} in order to accommodate
 * some particular conditions needed by CATS.
 */
public abstract class CatsModelUtils {

    private CatsModelUtils() {
        //ntd
    }

    public static boolean isStringSchema(Schema<?> schema) {
        return ModelUtils.isStringSchema(schema);
    }

    public static boolean isBooleanSchema(Schema<?> schema) {
        return ModelUtils.isBooleanSchema(schema);
    }

    public static boolean isArraySchema(Schema<?> schema) {
        return ModelUtils.isArraySchema(schema) || "array".equalsIgnoreCase(schema.getType());
    }

    public static boolean isNumberSchema(Schema<?> schema) {
        return ModelUtils.isNumberSchema(schema);
    }

    public static boolean isFloatSchema(Schema<?> schema) {
        return SchemaTypeUtil.FLOAT_FORMAT.equalsIgnoreCase(schema.getFormat());
    }

    public static boolean isDecimalSchema(Schema<?> schema) {
        return "number".equalsIgnoreCase(schema.getFormat());
    }

    public static boolean isShortIntegerSchema(Schema<?> schema) {
        return SchemaTypeUtil.INTEGER32_FORMAT.equalsIgnoreCase(schema.getFormat());
    }

    public static boolean isIntegerSchema(Schema<?> schema) {
        return ModelUtils.isIntegerSchema(schema);
    }

    public static boolean isDateSchema(Schema<?> schema) {
        return ModelUtils.isDateSchema(schema);
    }

    public static boolean isDateTimeSchema(Schema<?> schema) {
        return ModelUtils.isDateTimeSchema(schema);
    }

    public static boolean isBinarySchema(Schema<?> schema) {
        return ModelUtils.isBinarySchema(schema);
    }

    public static boolean isByteArraySchema(Schema<?> schema) {
        return ModelUtils.isByteArraySchema(schema);
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
                (SchemaTypeUtil.OBJECT_TYPE.equals(schema.getType()) && !(schema instanceof MapSchema) && !isComposedSchema(schema));
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
     * Eliminates the last part of the string if it matches the previous one.
     *
     * @param input The input string separated by ".".
     * @return The modified string with the last duplicate part removed.
     */
    public static String eliminateDuplicatePart(String input) {
        return Stream.of(input.split("\\."))
                .reduce((prev, curr) -> {
                    if (prev.endsWith(curr)) {
                        return prev;
                    } else {
                        return prev + "." + curr;
                    }
                })
                .orElse("");
    }
}
