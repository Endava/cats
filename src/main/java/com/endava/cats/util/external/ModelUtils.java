package com.endava.cats.util.external;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ModelUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelUtils.class);
    private static final String X_PARENT = "x-parent";

    public static boolean isDisallowAdditionalPropertiesIfNotPresent() {
        return true;
    }

    /**
     * Return the list of unused schemas in the 'components/schemas' section of an openAPI specification
     *
     * @param openAPI specification
     * @return schemas a list of unused schemas
     */
    public static List<String> getUnusedSchemas(OpenAPI openAPI) {
        final Map<String, List<String>> childrenMap;
        Map<String, List<String>> tmpChildrenMap;
        try {
            tmpChildrenMap = getChildrenMap(openAPI);
        } catch (NullPointerException npe) {
            // in rare cases, such as a spec document with only one top-level oneOf schema and multiple referenced schemas,
            // the stream used in getChildrenMap will raise an NPE. Rather than modify getChildrenMap which is used by getAllUsedSchemas,
            // we'll catch here as a workaround for this edge case.
            tmpChildrenMap = new HashMap<>();
        }

        childrenMap = tmpChildrenMap;
        List<String> unusedSchemas = new ArrayList<String>();

        if (openAPI != null) {
            Map<String, Schema> schemas = getSchemas(openAPI);
            unusedSchemas.addAll(schemas.keySet());

            visitOpenAPI(openAPI, (s, t) -> {
                if (s.get$ref() != null) {
                    String ref = getSimpleRef(s.get$ref());
                    unusedSchemas.remove(ref);
                    if (childrenMap.containsKey(ref)) {
                        unusedSchemas.removeAll(childrenMap.get(ref));
                    }
                }
            });
        }
        return unusedSchemas;
    }


    /**
     * Private method used by several methods like
     * {@link #getUnusedSchemas(OpenAPI)}. It traverses all paths of an
     * OpenAPI instance and call the visitor functional interface when a schema is found.
     *
     * @param openAPI specification
     * @param visitor functional interface (can be defined as a lambda) called each time a schema is found.
     */
    private static void visitOpenAPI(OpenAPI openAPI, ModelUtils.OpenAPISchemaVisitor visitor) {
        Map<String, PathItem> paths = openAPI.getPaths();
        List<String> visitedSchemas = new ArrayList<>();

        if (paths != null) {
            for (PathItem path : paths.values()) {
                visitPathItem(path, openAPI, visitor, visitedSchemas);
            }
        }
    }

    private static void visitPathItem(PathItem pathItem, OpenAPI openAPI, ModelUtils.OpenAPISchemaVisitor visitor, List<String> visitedSchemas) {
        List<Operation> allOperations = pathItem.readOperations();
        if (allOperations != null) {
            for (Operation operation : allOperations) {
                //Params:
                visitParameters(openAPI, operation.getParameters(), visitor, visitedSchemas);

                //RequestBody:
                RequestBody requestBody = getReferencedRequestBody(openAPI, operation.getRequestBody());
                if (requestBody != null) {
                    visitContent(openAPI, requestBody.getContent(), visitor, visitedSchemas);
                }

                //Responses:
                if (operation.getResponses() != null) {
                    for (ApiResponse r : operation.getResponses().values()) {
                        ApiResponse apiResponse = getReferencedApiResponse(openAPI, r);
                        if (apiResponse != null) {
                            visitContent(openAPI, apiResponse.getContent(), visitor, visitedSchemas);
                            if (apiResponse.getHeaders() != null) {
                                for (Entry<String, Header> e : apiResponse.getHeaders().entrySet()) {
                                    Header header = getReferencedHeader(openAPI, e.getValue());
                                    if (header.getSchema() != null) {
                                        visitSchema(openAPI, header.getSchema(), e.getKey(), visitedSchemas, visitor);
                                    }
                                    visitContent(openAPI, header.getContent(), visitor, visitedSchemas);
                                }
                            }
                        }
                    }
                }

                //Callbacks:
                if (operation.getCallbacks() != null) {
                    for (Callback c : operation.getCallbacks().values()) {
                        Callback callback = getReferencedCallback(openAPI, c);
                        if (callback != null) {
                            for (PathItem p : callback.values()) {
                                visitPathItem(p, openAPI, visitor, visitedSchemas);
                            }
                        }
                    }
                }
            }
        }
        //Params:
        visitParameters(openAPI, pathItem.getParameters(), visitor, visitedSchemas);
    }

    private static void visitParameters(OpenAPI openAPI, List<Parameter> parameters, ModelUtils.OpenAPISchemaVisitor visitor,
                                        List<String> visitedSchemas) {
        if (parameters != null) {
            for (Parameter p : parameters) {
                Parameter parameter = getReferencedParameter(openAPI, p);
                if (parameter != null) {
                    if (parameter.getSchema() != null) {
                        visitSchema(openAPI, parameter.getSchema(), null, visitedSchemas, visitor);
                    }
                    visitContent(openAPI, parameter.getContent(), visitor, visitedSchemas);
                }
            }
        }
    }

    private static void visitContent(OpenAPI openAPI, Content content, ModelUtils.OpenAPISchemaVisitor visitor, List<String> visitedSchemas) {
        if (content != null) {
            for (Entry<String, io.swagger.v3.oas.models.media.MediaType> e : content.entrySet()) {
                if (e.getValue().getSchema() != null) {
                    visitSchema(openAPI, e.getValue().getSchema(), e.getKey(), visitedSchemas, visitor);
                }
            }
        }
    }

    /**
     * Invoke the specified visitor function for every schema that matches mimeType in the OpenAPI document.
     * <p>
     * To avoid infinite recursion, referenced schemas are visited only once. When a referenced schema is visited,
     * it is added to visitedSchemas.
     *
     * @param openAPI        the OpenAPI document that contains schema objects.
     * @param schema         the root schema object to be visited.
     * @param mimeType       the mime type. TODO: does not seem to be used in a meaningful way.
     * @param visitedSchemas the list of referenced schemas that have been visited.
     * @param visitor        the visitor function which is invoked for every visited schema.
     */
    private static void visitSchema(OpenAPI openAPI, Schema schema, String mimeType, List<String> visitedSchemas, ModelUtils.OpenAPISchemaVisitor visitor) {
        if (schema == null) {
            return;
        }

        visitor.visit(schema, mimeType);
        if (schema.get$ref() != null) {
            String ref = getSimpleRef(schema.get$ref());
            if (!visitedSchemas.contains(ref)) {
                visitedSchemas.add(ref);
                Schema referencedSchema = getSchemas(openAPI).get(ref);
                if (referencedSchema != null) {
                    visitSchema(openAPI, referencedSchema, mimeType, visitedSchemas, visitor);
                }
            }
        }
        if (isComposedSchema(schema)) {
            List<Schema> oneOf = schema.getOneOf();
            if (oneOf != null) {
                for (Schema s : oneOf) {
                    visitSchema(openAPI, s, mimeType, visitedSchemas, visitor);
                }
            }
            List<Schema> allOf = schema.getAllOf();
            if (allOf != null) {
                for (Schema s : allOf) {
                    visitSchema(openAPI, s, mimeType, visitedSchemas, visitor);
                }
            }
            List<Schema> anyOf = schema.getAnyOf();
            if (anyOf != null) {
                for (Schema s : anyOf) {
                    visitSchema(openAPI, s, mimeType, visitedSchemas, visitor);
                }
            }
        } else if (ModelUtils.isArraySchema(schema)) {
            Schema itemsSchema = ModelUtils.getSchemaItems(schema);
            if (itemsSchema != null) {
                visitSchema(openAPI, itemsSchema, mimeType, visitedSchemas, visitor);
            }
        } else if (isMapSchema(schema)) {
            Object additionalProperties = schema.getAdditionalProperties();
            if (additionalProperties instanceof Schema additionalPropSchema) {
                visitSchema(openAPI, additionalPropSchema, mimeType, visitedSchemas, visitor);
            }
        }
        if (schema.getNot() != null) {
            visitSchema(openAPI, schema.getNot(), mimeType, visitedSchemas, visitor);
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            for (Schema property : properties.values()) {
                visitSchema(openAPI, property, null, visitedSchemas, visitor);
            }
        }
    }

    public static String getSimpleRef(String ref) {
        if (ref == null) {
            //throw new RuntimeException("Failed to get the schema: null");
            return null;
        } else if (ref.startsWith("#/components/")) {
            ref = ref.substring(ref.lastIndexOf("/") + 1);
        } else if (ref.startsWith("#/definitions/")) {
            ref = ref.substring(ref.lastIndexOf("/") + 1);
        } else {
            //throw new RuntimeException("Failed to get the schema: " + ref);
            return null;
        }

        ref = URLDecoder.decode(ref, StandardCharsets.UTF_8);

        // see https://tools.ietf.org/html/rfc6901#section-3
        // Because the characters '~' (%x7E) and '/' (%x2F) have special meanings in
        // JSON Pointer, '~' needs to be encoded as '~0' and '/' needs to be encoded
        // as '~1' when these characters appear in a reference token.
        // This reverses that encoding.
        ref = ref.replace("~1", "/").replace("~0", "~");

        return ref;
    }

    /**
     * Return true if the specified schema is an object with a fixed number of properties.
     * <p>
     * A ObjectSchema differs from a MapSchema in the following way:
     * - An ObjectSchema is not extensible, i.e. it has a fixed number of properties.
     * - A MapSchema is an object that can be extended with an arbitrary set of properties.
     * The payload may include dynamic properties.
     * <p>
     * For example, an OpenAPI schema is considered an ObjectSchema in the following scenarios:
     * <p>
     * <p>
     * type: object
     * additionalProperties: false
     * properties:
     * name:
     * type: string
     * address:
     * type: string
     *
     * @param schema the OAS schema
     * @return true if the specified schema is an Object schema.
     */
    public static boolean isObjectSchema(Schema schema) {
        if (schema == null) {
            return false;
        }

        return schema instanceof ObjectSchema ||
                // must not be a map
                (SchemaTypeUtil.OBJECT_TYPE.equals(getType(schema)) && !ModelUtils.isMapSchema(schema)) ||
                // must have at least one property
                (getType(schema) == null && schema.getProperties() != null && !schema.getProperties().isEmpty());
    }

    /**
     * Return true if the specified schema is composed, i.e. if it uses
     * 'oneOf', 'anyOf' or 'allOf'.
     *
     * @param schema the OAS schema
     * @return true if the specified schema is a Composed schema.
     */
    public static boolean isComposedSchema(Schema schema) {
        if (schema == null) {
            return false;
        }

        // in 3.0, ComposeSchema is used for anyOf/oneOf/allOf
        // in 3.1, it's not the case so we need more checks below
        if (schema instanceof ComposedSchema) {
            return true;
        }

        // has oneOf
        if (schema.getOneOf() != null) {
            return true;
        }

        // has anyOf
        if (schema.getAnyOf() != null) {
            return true;
        }

        // has allOf
        if (schema.getAllOf() != null) {
            return true;
        }

        return false;
    }

    /**
     * Return true if the specified 'schema' is an object that can be extended with additional properties.
     * Additional properties means a Schema should support all explicitly defined properties plus any
     * undeclared properties.
     * <p>
     * A MapSchema differs from an ObjectSchema in the following way:
     * - An ObjectSchema is not extensible, i.e. it has a fixed number of properties.
     * - A MapSchema is an object that can be extended with an arbitrary set of properties.
     * The payload may include dynamic properties.
     * <p>
     * Note that isMapSchema returns true for a composed schema (allOf, anyOf, oneOf) that also defines
     * additionalproperties.
     * <p>
     * For example, an OpenAPI schema is considered a MapSchema in the following scenarios:
     * <p>
     * <p>
     * type: object
     * additionalProperties: true
     * <p>
     * type: object
     * additionalProperties:
     * type: object
     * properties:
     * code:
     * type: integer
     * <p>
     * allOf:
     * - $ref: '#/components/schemas/Class1'
     * - $ref: '#/components/schemas/Class2'
     * additionalProperties: true
     *
     * @param schema the OAS schema
     * @return true if the specified schema is a Map schema.
     */
    public static boolean isMapSchema(Schema schema) {
        if (schema == null) {
            return false;
        }

        // additionalProperties explicitly set to false
        if ((schema.getAdditionalProperties() instanceof Boolean && Boolean.FALSE.equals(schema.getAdditionalProperties())) ||
                (schema.getAdditionalProperties() instanceof Schema && Boolean.FALSE.equals(((Schema) schema.getAdditionalProperties()).getBooleanSchemaValue()))
        ) {
            return false;
        }

        if (schema instanceof JsonSchema) { // 3.1 spec
            return ((schema.getAdditionalProperties() instanceof Schema) ||
                    (schema.getAdditionalProperties() instanceof Boolean && (Boolean) schema.getAdditionalProperties()));
        } else { // 3.0 or 2.x spec
            return (schema instanceof MapSchema) ||
                    (schema.getAdditionalProperties() instanceof Schema) ||
                    (schema.getAdditionalProperties() instanceof Boolean && (Boolean) schema.getAdditionalProperties());
        }
    }

    /**
     * Return true if the specified schema is an array of items.
     *
     * @param schema the OAS schema
     * @return true if the specified schema is an Array schema.
     */
    public static boolean isArraySchema(Schema schema) {
        if (schema == null) {
            return false;
        }
        return (schema instanceof ArraySchema) || "array".equals(getType(schema));
    }

    /**
     * Return the schema in the array's item. Null if schema is not an array.
     *
     * @param schema the OAS schema
     * @return item schema.
     */
    public static Schema<?> getSchemaItems(Schema schema) {
        if (!isArraySchema(schema)) {
            return null;
        }

        Schema<?> items = schema.getItems();
        if (items == null) {
            if (schema instanceof JsonSchema) { // 3.1 spec
                // set the items to a new schema (any type)
                items = new Schema<>();
                schema.setItems(items);
            } else { // 3.0 spec, default to string
                LOGGER.error("Undefined array inner type for `{}`. Default to String.", schema.getName());
                items = new StringSchema().description("TODO default missing array inner type to string");
                schema.setItems(items);
            }
        }
        return items;
    }


    public static boolean isEnumSchema(final Schema<?> schema) {
        // MyEnum:
        //   type: string
        //   enum:
        //   - ENUM_1
        //   - ENUM_2
        return schema.getEnum() != null
                && !schema.getEnum().isEmpty();
    }

    public static Schema getSchema(OpenAPI openAPI, String name) {
        if (name == null) {
            return null;
        }

        return getSchemas(openAPI).get(name);
    }

    /**
     * Return a Map of the schemas defined under /components/schemas in the OAS document.
     * The returned Map only includes the direct children of /components/schemas in the OAS document; the Map
     * does not include inlined schemas.
     *
     * @param openAPI the OpenAPI document.
     * @return a map of schemas in the OAS document.
     */
    public static Map<String, Schema> getSchemas(OpenAPI openAPI) {
        if (openAPI != null && openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            return openAPI.getComponents().getSchemas();
        }
        return Collections.emptyMap();
    }

    /**
     * If a RequestBody contains a reference to another RequestBody with '$ref', returns the referenced RequestBody if it is found or the actual RequestBody in the other cases.
     *
     * @param openAPI     specification being checked
     * @param requestBody potentially containing a '$ref'
     * @return requestBody without '$ref'
     */
    public static RequestBody getReferencedRequestBody(OpenAPI openAPI, RequestBody requestBody) {
        if (requestBody != null && StringUtils.isNotEmpty(requestBody.get$ref())) {
            String name = getSimpleRef(requestBody.get$ref());
            RequestBody referencedRequestBody = getRequestBody(openAPI, name);
            if (referencedRequestBody != null) {
                return referencedRequestBody;
            }
        }
        return requestBody;
    }

    public static RequestBody getRequestBody(OpenAPI openAPI, String name) {
        if (name == null) {
            return null;
        }

        if (openAPI != null && openAPI.getComponents() != null && openAPI.getComponents().getRequestBodies() != null) {
            return openAPI.getComponents().getRequestBodies().get(name);
        }
        return null;
    }

    /**
     * If a ApiResponse contains a reference to another ApiResponse with '$ref', returns the referenced ApiResponse if it is found or the actual ApiResponse in the other cases.
     *
     * @param openAPI     specification being checked
     * @param apiResponse potentially containing a '$ref'
     * @return apiResponse without '$ref'
     */
    public static ApiResponse getReferencedApiResponse(OpenAPI openAPI, ApiResponse apiResponse) {
        if (apiResponse != null && StringUtils.isNotEmpty(apiResponse.get$ref())) {
            String name = getSimpleRef(apiResponse.get$ref());
            ApiResponse referencedApiResponse = getApiResponse(openAPI, name);
            if (referencedApiResponse != null) {
                return referencedApiResponse;
            }
        }
        return apiResponse;
    }

    public static ApiResponse getApiResponse(OpenAPI openAPI, String name) {
        if (name != null && openAPI != null && openAPI.getComponents() != null && openAPI.getComponents().getResponses() != null) {
            return openAPI.getComponents().getResponses().get(name);
        }
        return null;
    }

    /**
     * If a Parameter contains a reference to another Parameter with '$ref', returns the referenced Parameter if it is found or the actual Parameter in the other cases.
     *
     * @param openAPI   specification being checked
     * @param parameter potentially containing a '$ref'
     * @return parameter without '$ref'
     */
    public static Parameter getReferencedParameter(OpenAPI openAPI, Parameter parameter) {
        if (parameter != null && StringUtils.isNotEmpty(parameter.get$ref())) {
            String name = getSimpleRef(parameter.get$ref());
            Parameter referencedParameter = getParameter(openAPI, name);
            if (referencedParameter != null) {
                return referencedParameter;
            }
        }
        return parameter;
    }

    public static Parameter getParameter(OpenAPI openAPI, String name) {
        if (name != null && openAPI != null && openAPI.getComponents() != null && openAPI.getComponents().getParameters() != null) {
            return openAPI.getComponents().getParameters().get(name);
        }
        return null;
    }

    /**
     * If a Callback contains a reference to another Callback with '$ref', returns the referenced Callback if it is found or the actual Callback in the other cases.
     *
     * @param openAPI  specification being checked
     * @param callback potentially containing a '$ref'
     * @return callback without '$ref'
     */
    public static Callback getReferencedCallback(OpenAPI openAPI, Callback callback) {
        if (callback != null && StringUtils.isNotEmpty(callback.get$ref())) {
            String name = getSimpleRef(callback.get$ref());
            Callback referencedCallback = getCallback(openAPI, name);
            if (referencedCallback != null) {
                return referencedCallback;
            }
        }
        return callback;
    }

    public static Callback getCallback(OpenAPI openAPI, String name) {
        if (name != null && openAPI != null && openAPI.getComponents() != null && openAPI.getComponents().getCallbacks() != null) {
            return openAPI.getComponents().getCallbacks().get(name);
        }
        return null;
    }


    /**
     * Returns the additionalProperties Schema for the specified input schema.
     * <p>
     * The additionalProperties keyword is used to control the handling of additional, undeclared
     * properties, that is, properties whose names are not listed in the properties keyword.
     * The additionalProperties keyword may be either a boolean or an object.
     * If additionalProperties is a boolean and set to false, no additional properties are allowed.
     * By default when the additionalProperties keyword is not specified in the input schema,
     * any additional properties are allowed. This is equivalent to setting additionalProperties
     * to the boolean value True or setting additionalProperties: {}
     *
     * @param schema the input schema that may or may not have the additionalProperties keyword.
     * @return the Schema of the additionalProperties. The null value is returned if no additional
     * properties are allowed.
     */
    public static Schema getAdditionalProperties(Schema schema) {
        Object addProps = schema.getAdditionalProperties();
        if (addProps instanceof Schema props) {
            return props;
        }
        if (addProps == null) {
            // When reaching this code path, this should indicate the 'additionalProperties' keyword is
            // not present in the OAS schema. This is true for OAS 3.0 documents.
            // However, the parsing logic is broken for OAS 2.0 documents because of the
            // https://github.com/swagger-api/swagger-parser/issues/1369 issue.
            // When OAS 2.0 documents are parsed, the swagger-v2-converter ignores the 'additionalProperties'
            // keyword if the value is boolean. That means codegen is unable to determine whether
            // additional properties are allowed or not.
            //
            // The original behavior was to assume additionalProperties had been set to false.
            if (isDisallowAdditionalPropertiesIfNotPresent()) {
                // If the 'additionalProperties' keyword is not present in a OAS schema,
                // interpret as if the 'additionalProperties' keyword had been set to false.
                // This is NOT compliant with the JSON schema specification. It is the original
                // 'openapi-generator' behavior.
                return null;
            }
            /*
            // The disallowAdditionalPropertiesIfNotPresent CLI option has been set to true,
            // but for now that only works with OAS 3.0 documents.
            // The new behavior does not work with OAS 2.0 documents.
            if (extensions == null || !extensions.containsKey(EXTENSION_OPENAPI_DOC_VERSION)) {
                // Fallback to the legacy behavior.
                return null;
            }
            // Get original swagger version from OAS extension.
            // Note openAPI.getOpenapi() is always set to 3.x even when the document
            // is converted from a OAS/Swagger 2.0 document.
            // https://github.com/swagger-api/swagger-parser/pull/1374
            SemVer version = new SemVer((String)extensions.get(EXTENSION_OPENAPI_DOC_VERSION));
            if (version.major != 3) {
                return null;
            }
            */
        }
        if (addProps == null || (addProps instanceof Boolean booleanProps && booleanProps)) {
            // Return an empty schema as the properties can take on any type per
            // the spec. See
            // https://github.com/OpenAPITools/openapi-generator/issues/9282 for
            // more details.
            return new Schema();
        }
        return null;
    }

    public static Header getReferencedHeader(OpenAPI openAPI, Header header) {
        if (header != null && StringUtils.isNotEmpty(header.get$ref())) {
            String name = getSimpleRef(header.get$ref());
            Header referencedheader = getHeader(openAPI, name);
            if (referencedheader != null) {
                return referencedheader;
            }
        }
        return header;
    }

    public static Header getHeader(OpenAPI openAPI, String name) {
        if (name != null && openAPI != null && openAPI.getComponents() != null && openAPI.getComponents().getHeaders() != null) {
            return openAPI.getComponents().getHeaders().get(name);
        }
        return null;
    }

    public static Map<String, List<String>> getChildrenMap(OpenAPI openAPI) {
        Map<String, Schema> allSchemas = getSchemas(openAPI);

        Map<String, List<Entry<String, Schema>>> groupedByParent = allSchemas.entrySet().stream()
                .filter(entry -> isComposedSchema(entry.getValue()))
                .filter(entry -> getParentName((Schema) entry.getValue(), allSchemas) != null)
                .collect(Collectors.groupingBy(entry -> getParentName((Schema) entry.getValue(), allSchemas)));

        return groupedByParent.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().stream().map(e -> e.getKey()).collect(Collectors.toList())));
    }

    /**
     * Get the interfaces from the schema (composed)
     *
     * @param composed schema (alias or direct reference)
     * @return a list of schema defined in allOf, anyOf or oneOf
     */
    public static List<Schema> getInterfaces(Schema composed) {
        if (composed.getAllOf() != null && !composed.getAllOf().isEmpty()) {
            return composed.getAllOf();
        } else if (composed.getAnyOf() != null && !composed.getAnyOf().isEmpty()) {
            return composed.getAnyOf();
        } else if (composed.getOneOf() != null && !composed.getOneOf().isEmpty()) {
            return composed.getOneOf();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Get the parent model name from the composed schema (allOf, anyOf, oneOf).
     * It traverses the OAS model (possibly resolving $ref) to determine schemas
     * that specify a determinator.
     * If there are multiple elements in the composed schema and it is not clear
     * which one should be the parent, return null.
     * <p>
     * For example, given the following OAS spec, the parent of 'Dog' is Animal
     * because 'Animal' specifies a discriminator.
     * <p>
     * animal:
     * type: object
     * discriminator:
     * propertyName: type
     * properties:
     * type: string
     *
     * <p>
     * dog:
     * allOf:
     * - $ref: '#/components/schemas/animal'
     * - type: object
     * properties:
     * breed: string
     *
     * @param composedSchema schema (alias or direct reference)
     * @param allSchemas     all schemas
     * @return the name of the parent model
     */
    public static String getParentName(Schema composedSchema, Map<String, Schema> allSchemas) {
        List<Schema> interfaces = getInterfaces(composedSchema);

        if (interfaces != null && !interfaces.isEmpty()) {
            List<String> parentNameCandidates = new ArrayList<>(interfaces.size());
            for (Schema schema : interfaces) {
                // get the actual schema
                if (StringUtils.isNotEmpty(schema.get$ref())) {
                    String parentName = getSimpleRef(schema.get$ref());
                    Schema s = allSchemas.get(parentName);
                    if (s == null) {
                        LOGGER.error("Failed to obtain schema from {}", parentName);
                        parentNameCandidates.add("UNKNOWN_PARENT_NAME");
                    } else if (hasOrInheritsDiscriminator(s, allSchemas, new ArrayList<Schema>())) {
                        // discriminator.propertyName is used or x-parent is used
                        parentNameCandidates.add(parentName);
                    }
                }
            }
            if (parentNameCandidates.size() > 1) {
                // unclear which one should be the parent
                return null;
            } else if (parentNameCandidates.size() == 1) {
                return parentNameCandidates.getFirst();
            }
        }

        return null;
    }


    private static boolean hasOrInheritsDiscriminator(Schema schema, Map<String, Schema> allSchemas, List<Schema> visitedSchemas) {
        for (Schema s : visitedSchemas) {
            if (s == schema) {
                return false;
            }
        }
        visitedSchemas.add(schema);

        if ((schema.getDiscriminator() != null && StringUtils.isNotEmpty(schema.getDiscriminator().getPropertyName()))
                || isExtensionParent(schema)) { // x-parent is used
            return true;
        } else if (StringUtils.isNotEmpty(schema.get$ref())) {
            String parentName = getSimpleRef(schema.get$ref());
            Schema s = allSchemas.get(parentName);
            if (s != null) {
                return hasOrInheritsDiscriminator(s, allSchemas, visitedSchemas);
            } else {
                LOGGER.error("Failed to obtain schema from {}", parentName);
            }
        } else if (isComposedSchema(schema)) {
            final List<Schema> interfaces = getInterfaces(schema);
            for (Schema i : interfaces) {
                if (hasOrInheritsDiscriminator(i, allSchemas, visitedSchemas)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * If it's a boolean, returns the value of the extension `x-parent`.
     * If it's string, return true if it's non-empty.
     * If the return value is `true`, the schema is a parent.
     *
     * @param schema Schema
     * @return boolean
     */
    public static boolean isExtensionParent(Schema schema) {
        if (schema == null || schema.getExtensions() == null) {
            return false;
        }

        Object xParent = schema.getExtensions().get(X_PARENT);
        if (xParent == null) {
            return false;
        } else if (xParent instanceof Boolean xParentB) {
            return xParentB;
        } else if (xParent instanceof String xParentS) {
            return StringUtils.isNotEmpty(xParentS);
        } else {
            return false;
        }
    }

    /**
     * isNullType returns true if the input schema is the 'null' type.
     * <p>
     * The 'null' type is supported in OAS 3.1 and above. It is not supported
     * in OAS 2.0 and OAS 3.0.x.
     * <p>
     * For example, the "null" type could be used to specify that a value must
     * either be null or a specified type:
     * <p>
     * OptionalOrder:
     * oneOf:
     * - type: 'null'
     * - $ref: '#/components/schemas/Order'
     *
     * @param schema the OpenAPI schema
     * @return true if the schema is the 'null' type
     */
    public static boolean isNullType(Schema schema) {
        return "null".equals(getType(schema));
    }


    /**
     * Returns true if the schema contains allOf but
     * no properties/oneOf/anyOf defined.
     *
     * @param schema the schema
     * @return true if the schema contains allOf but no properties/oneOf/anyOf defined.
     */
    public static boolean isAllOf(Schema schema) {
        if (hasAllOf(schema) && (schema.getProperties() == null || schema.getProperties().isEmpty()) &&
                (schema.getOneOf() == null || schema.getOneOf().isEmpty()) &&
                (schema.getAnyOf() == null || schema.getAnyOf().isEmpty())) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if the schema contains allOf and may or may not have
     * properties/oneOf/anyOf defined.
     *
     * @param schema the schema
     * @return true if allOf is not empty
     */
    public static boolean hasAllOf(Schema schema) {
        if (schema != null && schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if the schema contains allOf and properties,
     * and no oneOf/anyOf defined.
     *
     * @param schema the schema
     * @return true if the schema contains allOf but no properties/oneOf/anyOf defined.
     */
    public static boolean isAllOfWithProperties(Schema schema) {
        return hasAllOf(schema) && (schema.getProperties() != null && !schema.getProperties().isEmpty()) &&
                (schema.getOneOf() == null || schema.getOneOf().isEmpty()) &&
                (schema.getAnyOf() == null || schema.getAnyOf().isEmpty());
    }

    /**
     * Returns true if the schema contains oneOf but
     * no properties/allOf/anyOf defined.
     *
     * @param schema the schema
     * @return true if the schema contains oneOf but no properties/allOf/anyOf defined.
     */
    public static boolean isOneOf(Schema schema) {
        if (schema == null) {
            return false;
        }

        if (hasOneOf(schema) && (schema.getProperties() == null || schema.getProperties().isEmpty()) &&
                (schema.getAllOf() == null || schema.getAllOf().isEmpty()) &&
                (schema.getAnyOf() == null || schema.getAnyOf().isEmpty())) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if the schema contains oneOf and may or may not have
     * properties/allOf/anyOf defined.
     *
     * @param schema the schema
     * @return true if allOf is not empty
     */
    public static boolean hasOneOf(Schema schema) {
        if (schema != null && schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if the schema contains anyOf but
     * no properties/allOf/anyOf defined.
     *
     * @param schema the schema
     * @return true if the schema contains oneOf but no properties/allOf/anyOf defined.
     */
    public static boolean isAnyOf(Schema schema) {
        if (schema == null) {
            return false;
        }

        if (hasAnyOf(schema) && (schema.getProperties() == null || schema.getProperties().isEmpty()) &&
                (schema.getAllOf() == null || schema.getAllOf().isEmpty()) &&
                (schema.getOneOf() == null || schema.getOneOf().isEmpty())) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if the schema contains anyOf and may or may not have
     * properties/allOf/oneOf defined.
     *
     * @param schema the schema
     * @return true if anyOf is not empty
     */
    public static boolean hasAnyOf(Schema schema) {
        if (schema != null && schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * Returns schema type.
     * For 3.1 spec, return the first one.
     *
     * @param schema the schema
     * @return schema type
     */
    public static String getType(Schema schema) {
        if (schema == null) {
            return null;
        }

        if (schema instanceof JsonSchema) {
            if (schema.getTypes() != null && !schema.getTypes().isEmpty()) {
                return String.valueOf(schema.getTypes().iterator().next());
            } else {
                return null;
            }
        } else {
            return schema.getType();
        }
    }


    @FunctionalInterface
    private interface OpenAPISchemaVisitor {

        void visit(Schema schema, String mimeType);
    }
}
