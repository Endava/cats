package com.endava.cats.openapi;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A modified version of {@code  org.openapitools.codegen.examples.ExampleGenerator} that takes into consideration several other request
 * setups including complex objects, array of objects, self-referencing objects, multi-level oneOf/anyOf, etc.
 * <p>
 * This is a <b>stateful</b> object. Don't use it through dependency injection.
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
@Slf4j
public class OpenAPIModelGeneratorV2 {
    public static final String SYNTH_SCHEMA_NAME = "CatsGetSchema";
    public static final String DEFAULT_STRING_WHEN_GENERATION_FAILS = "addOrChangeOrSimplifyThePattern";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final BigDecimal MAX = new BigDecimal("99999999999");
    public static final int LIMIT_OF_EXAMPLES = 500;
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(OpenAPIModelGeneratorV2.class);
    private final Random random;
    private final ProcessingArguments.ExamplesFlags examplesFlags;
    private final CatsGlobalContext globalContext;
    private final ValidDataFormat validDataFormat;
    private final int selfReferenceDepth;
    private final Map<String, Integer> callStackCounter;
    private final boolean useDefaults;
    private final int maxArraySize;
    private final Map<String, List<Map<String, Object>>> examplesCache = new HashMap<>();

    @Getter
    private final Map<String, Schema> requestDataTypes = new HashMap<>();

    private String currentProperty = "";
    private final boolean resolveAnyOfAsMultipleSchema;
    private int currentPropertiesDepth;
    private int totalDepth = 50;

    /**
     * Constructs an OpenAPIModelGeneratorV2 with the specified configuration.
     * The default value for {@code resolveAnyOfAsMultipleSchema=true}. The default value for {@code totalDepth=0} which means no restriction.
     *
     * @param catsGlobalContext   The global context for CATS.
     * @param validDataFormat     The format to use for generating valid data.
     * @param useExamplesArgument Flag indicating whether to use examples from the OpenAPI specification.
     * @param selfReferenceDepth  The maximum depth for generating self-referencing models.
     * @param useDefaults         Whether to use default values if available
     * @param maxArraySize        The maximum size for arrays
     */
    public OpenAPIModelGeneratorV2(CatsGlobalContext catsGlobalContext, ValidDataFormat validDataFormat, ProcessingArguments.ExamplesFlags useExamplesArgument, int selfReferenceDepth, boolean useDefaults, int maxArraySize) {
        this.globalContext = catsGlobalContext;
        this.random = CatsUtil.random();
        this.examplesFlags = useExamplesArgument;
        this.selfReferenceDepth = selfReferenceDepth;
        this.validDataFormat = validDataFormat;
        this.callStackCounter = new HashMap<>();
        this.useDefaults = useDefaults;
        this.maxArraySize = maxArraySize;

        this.resolveAnyOfAsMultipleSchema = true;
        this.totalDepth = 0;
    }

    /**
     * Constructs an OpenAPIModelGeneratorV2 with the specified configuration. The default value for {@code totalDepth=50}
     *
     * @param catsGlobalContext            The global context for CATS.
     * @param validDataFormat              The format to use for generating valid data.
     * @param useExamplesArgument          Flag indicating whether to use examples from the OpenAPI specification.
     * @param selfReferenceDepth           The maximum depth for generating self-referencing models.
     * @param resolveAnyOfAsMultipleSchema If true it will resolve all combinations of oneOf/anyOf schemas
     * @param useDefaults                  Whether to use default values if available
     * @param maxArraySize                 The maximum size for arrays
     */
    public OpenAPIModelGeneratorV2(CatsGlobalContext catsGlobalContext, ValidDataFormat validDataFormat, ProcessingArguments.ExamplesFlags useExamplesArgument, int selfReferenceDepth, boolean useDefaults, int maxArraySize, boolean resolveAnyOfAsMultipleSchema) {
        this.globalContext = catsGlobalContext;
        this.random = CatsUtil.random();
        this.examplesFlags = useExamplesArgument;
        this.selfReferenceDepth = selfReferenceDepth;
        this.validDataFormat = validDataFormat;
        this.callStackCounter = new HashMap<>();
        this.useDefaults = useDefaults;
        this.maxArraySize = maxArraySize;
        this.resolveAnyOfAsMultipleSchema = resolveAnyOfAsMultipleSchema;
    }

    private void addExampleAndKeepDepth(String propertyName, Object propertyExample, Map<String, Object> newExample, List<Map<String, Object>> combinedExamples) {
        logger.trace("addExampleAndKeepDepth for property {}", propertyName);
        switch (propertyExample) {
            case Map map when map.get(null) != null -> newExample.put(propertyName, map.values().iterator().next());
            case Map map when map.get(propertyName) != null && isNotCyclingReference(propertyName) ->
                    newExample.put(propertyName, map.get(propertyName));
            case null, default -> newExample.put(propertyName, propertyExample);
        }
        combinedExamples.add(newExample);
    }

    private boolean isNotCyclingReference(String propertyName) {
        return Arrays.stream(currentProperty.split("#", -1)).noneMatch(entry -> entry.equalsIgnoreCase(propertyName));
    }

    private static boolean isNullSchema(Schema innerType) {
        return (innerType.getType() == null || "null".equalsIgnoreCase(innerType.getType()))
                && innerType.get$ref() == null && !CatsModelUtils.isComposedSchema(innerType)
                && innerType.getProperties() == null && CollectionUtils.isEmpty(innerType.getTypes());
    }


    public List<String> generate(String modelName) {
        List<String> examples = new ArrayList<>();
        if (modelName != null) {
            final Schema schema = this.globalContext.getSchemaFromReference(modelName);
            if (schema != null) {
                List<Map<String, Object>> generatedExamples = generateExamplesForSchema(modelName, schema);
                for (Object generatedExample : generatedExamples) {
                    String example = this.transformInJson(modelName, generatedExample);

                    if (example != null) {
                        examples.add(example);
                    }
                }
                return examples.isEmpty() ? List.of("{}") : examples;
            } else {
                throw new IllegalArgumentException("Scheme is not declared: " + modelName);
            }
        }
        currentProperty = "";
        return Collections.emptyList();
    }

    private @Nullable String transformInJson(String modelName, Object generatedExample) {
        if (generatedExample instanceof Map map && map.get(null) != null) {
            generatedExample = map.get(null);
        }

        if (generatedExample instanceof Map map && map.get(modelName) != null) {
            generatedExample = map.get(modelName);
        }

        String example = JsonUtils.serialize(generatedExample);

        if (example == null) {
            example = JsonUtils.serializeWithDepthAwareSerializer(generatedExample);
            globalContext.recordError("Generate sample it's too large to be processed in memory. CATS used a limiting depth serializer which might not include all expected fields. Re-run CATS with a smaller --selfReferenceDepth value, like --selfReferenceDepth 2");
        }
        return example;
    }

    private <T> Object getExampleFromStringSchema(String propertyName, Schema<T> property) {
        logger.trace("getExampleFromStringSchema. String property {}", propertyName);

        if (property.getMinLength() != null || property.getMaxLength() != null) {
            return generateAndRecordIfExceptionThrown(propertyName, property.getPattern(),
                    () -> StringGenerator.generateValueBasedOnMinMax(property));
        }
        if (CatsModelUtils.isDecimalSchema(property)) {
            return generateBigDecimal(property);
        }
        if (property.getPattern() != null) {
            return generateAndRecordIfExceptionThrown(propertyName, property.getPattern(),
                    () -> StringGenerator.generate(property.getPattern(), -1, -1));
        }
        logger.trace("No constraints, generating alphanumeric string based on property length {}", propertyName);
        return StringGenerator.generate(StringGenerator.ALPHANUMERIC_PLUS, 2, DEFAULT_STRING_WHEN_GENERATION_FAILS.length());
    }

    private BigDecimal generateBigDecimal(Schema<?> schema) {
        BigDecimal min = schema.getMinimum() != null ? schema.getMinimum() : BigDecimal.ONE;
        BigDecimal max = schema.getMaximum() != null ? schema.getMaximum() : MAX;

        BigDecimal range = max.subtract(min);
        BigDecimal randomBigDecimal = min.add(range.multiply(BigDecimal.valueOf(random.nextDouble())));

        randomBigDecimal = randomBigDecimal.setScale(2, RoundingMode.HALF_UP);

        return randomBigDecimal;
    }

    private boolean isAnyComposedSchemaInChain(String schemaChain) {
        String[] schemaRefs = schemaChain.split("_");
        return Arrays.stream(schemaRefs).anyMatch(entry -> CatsModelUtils.isComposedSchema(globalContext.getSchemaFromReference(entry)));
    }

    private Schema normalizeDiscriminatorMappingsToOneOf(String name, Schema<?> schema) {
        logger.trace("normalizeDiscriminatorMappingsToOneOf for schema {}", name);
        if (schema != null && schema.getDiscriminator() != null && !CollectionUtils.isEmpty(schema.getDiscriminator().getMapping())
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

    private List<Map<String, Object>> getFromCacheOrExample(String cacheKey, Schema schema) {
        if (examplesCache.containsKey(cacheKey)) {
            return examplesCache.get(cacheKey);
        }

        Object fromExample = extractExampleFromSchema(schema, examplesFlags.useSchemaExamples());
        if (fromExample != null) {
            return List.of(formatExampleAsMap(fromExample));
        }
        return List.of();
    }

    private List<Map<String, Object>> generateExamplesForSchema(String name, Schema schema) {
        logger.trace("generateExamplesForSchema for schema {}", name);
        /* When checking for cyclic references make sure we exclude schema names. Some OpenAPI specs are generated by frameworks,
        so they might generate schema names that might seem cyclic such as: Body_Create_a_previously_generated_voice_v1_voice_generation_create_voice_post
         */
        if (JsonUtils.isCyclicReference(currentProperty, selfReferenceDepth) && globalContext.getSchemaFromReference(name) == null) {
            return List.of();
        }
        String cacheKey = name + "_" + schema.hashCode();

        List<Map<String, Object>> fromCacheOrExample = getFromCacheOrExample(cacheKey, schema);
        if (!fromCacheOrExample.isEmpty()) {
            return fromCacheOrExample;
        }

        List<Map<String, Object>> examples = new ArrayList<>();
        if (schema.get$ref() != null) {
            schema = globalContext.getSchemaFromReference(schema.get$ref());
        }

        fromCacheOrExample = getFromCacheOrExample(cacheKey, schema);
        if (!fromCacheOrExample.isEmpty()) {
            return fromCacheOrExample;
        }

        schema = normalizeDiscriminatorMappingsToOneOf(name, schema);

        if (schema == null) {
            return List.of();
        }

        recordRequestSchema(currentProperty, schema);

        // Handle properties
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            examples.addAll(traverseSchemaProperties(schema, name));
        }

        // Handle allOf
        if (CatsModelUtils.isAllOf(schema) || CatsModelUtils.isAllOfWithProperties(schema)) {
            List<Map<String, Object>> allOfExamples = resolveAllOfSchemaProperties(schema, name);
            examples = combineExampleLists(examples, allOfExamples);
        }

        // Handle oneOf and anyOf
        if (CatsModelUtils.isAnyOf(schema) || CatsModelUtils.isOneOf(schema)) {
            List<Map<String, Object>> oneOfAnyOfExamples = resolveAnyOfOneOfSchemaProperties(name, schema);
            examples = combineExampleLists(examples, oneOfAnyOfExamples);
        }

        // Handle array
        if (CatsModelUtils.isArraySchema(schema)) {
            List<Map<String, Object>> arrayExamples = resolveArraySchemaProperties(name, schema);
            examples = combineExampleLists(examples, arrayExamples);
        }

        // If no examples were generated, create a default one
        if (examples.isEmpty()) {
            Map<String, Object> defaultExample = new HashMap<>();
            Object resolvedExample = resolvePropertyToExample(name, schema);
            if (resolvedExample != null) {
                defaultExample.put(name, resolvedExample);
                examples.add(defaultExample);
            }
        }

        examplesCache.put(cacheKey, examples);

        return examples;
    }

    private Map formatExampleAsMap(Object fromExample) {
        if (!(fromExample instanceof Map)) {
            Map<String, Object> example = new HashMap<>();
            example.put(null, fromExample);
            return example;
        }
        return JsonUtils.getSimpleObjectMapper().convertValue(fromExample, Map.class);
    }

    private List<Map<String, Object>> traverseSchemaProperties(Schema schema, String currentSchemaName) {
        logger.trace("traverseSchemaProperties for schema {}", currentSchemaName);
        List<Map<String, Object>> examples = new ArrayList<>();

        Set<Map.Entry<String, Schema>> properties = schema.getProperties().entrySet();
        for (Map.Entry<String, Schema> entry : properties) {
            String propertyName = entry.getKey();
            Schema property = entry.getValue();

            currentPropertiesDepth++;
            // this is a hack to avoid infinite recursion when the schema references itself and JsonUtils.isCyclicReference does not catch it.
            // json responses tend to have a lot of self-references, and they return more complex objects than request payloads
            if (totalDepth > 0 && currentPropertiesDepth > totalDepth) {
                continue;
            }
            List<Object> propertyExamples;
            if (schema.getDiscriminator() != null && schema.getDiscriminator().getPropertyName().equalsIgnoreCase(propertyName)) {
                propertyExamples = List.of(matchToEnumOrEmpty(currentSchemaName, property, propertyName));
                recordRequestSchema(propertyName, property);
            } else {
                propertyExamples = resolvePropertyToExamples(propertyName, property);
            }

            if (!propertyExamples.isEmpty()) {
                examples = combineExamples(examples, propertyName, propertyExamples);
                currentPropertiesDepth--;
            }
        }

        return examples;
    }

    private List<Map<String, Object>> resolveAllOfSchemaProperties(Schema schema, String propertyName) {
        logger.trace("resolveAllOfSchemaProperties for schema {}", propertyName);
        List<Map<String, Object>> examples = new ArrayList<>();
        keepOriginalSchema(propertyName, schema);
        mergeRequiredProperties(schema.getAllOf());

        List<Schema> schemas = schema.getAllOf().stream().filter(iteratingSchema -> !isNullSchema((Schema<?>) iteratingSchema)).toList();
        for (Schema subSchema : schemas) {
            List<Map<String, Object>> subExamples = generateExamplesForSchema(propertyName, subSchema);
            List<Map<String, Object>> interimExamples = new ArrayList<>();

            if (CatsModelUtils.isAnyOf(subSchema) || CatsModelUtils.isOneOf(subSchema)) {
                interimExamples.addAll(subExamples);
            } else if (examples.isEmpty()) {
                examples.addAll(subExamples);
            } else {
                for (Map<String, Object> subExample : subExamples) {
                    for (Map<String, Object> existingExample : examples) {
                        interimExamples.add(mergeMaps(existingExample, subExample));
                    }
                }
            }
            if (!interimExamples.isEmpty()) {
                examples.clear();
            }
            examples.addAll(interimExamples.stream()
                    .limit(Math.min(interimExamples.size(), LIMIT_OF_EXAMPLES))
                    .toList());
        }

        return examples;
    }


    private List<Map<String, Object>> resolveAnyOfOneOfSchemaProperties(String propertyName, Schema schema) {
        if (resolveAnyOfAsMultipleSchema) {
            return resolveAnyOfOneOfSchemaPropertiesWithMultipleSchemas(propertyName, schema);
        }
        return List.of(flatMap(resolveAnyOfOneOfSchemaPropertiesWithMultipleSchemas(propertyName, schema)));
    }

    private static Map<String, Object> flatMap(List<Map<String, Object>> generatedExamples) {
        return generatedExamples.stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> {
                            if (existing instanceof List<?> existingList) {
                                List<Object> newList = new ArrayList<>(existingList);
                                newList.add(replacement);
                                return newList;
                            } else {
                                List<Object> list = new ArrayList<>();
                                list.add(existing);
                                list.add(replacement);
                                return list;
                            }
                        }
                ));
    }

    private List<Map<String, Object>> resolveAnyOfOneOfSchemaPropertiesWithMultipleSchemas(String propertyName, Schema schema) {
        logger.trace("resolveAnyOfOneOfSchemaProperties for schema {}", propertyName);
        mapDiscriminator(schema, Optional.ofNullable(schema.getAnyOf()).orElse(schema.getOneOf()));
        List<Map<String, Object>> examples = new ArrayList<>();
        List<Schema> schemas = CatsModelUtils.getInterfaces(schema).stream().filter(iteratingSchema -> !isNullSchema(iteratingSchema)).toList();
        String previousPropertyName = propertyName;

        for (Schema subSchema : schemas) {
            propertyName = previousPropertyName;
            if (hasValidRef(subSchema)) {
                Schema resolved = globalContext.getSchemaFromReference(subSchema.get$ref());
                if (resolved != null) {
                    propertyName = CatsModelUtils.getSimpleRefUsingOAT(subSchema.get$ref());
                    examples.addAll(generateExamplesForSchema(propertyName, resolved));
                }
            } else {
                examples.addAll(generateExamplesForSchema(propertyName, subSchema));
            }
        }

        return examples;
    }

    private List<Map<String, Object>> resolveArraySchemaProperties(String propertyName, Schema schema) {
        logger.trace("resolveArraySchemaProperties for schema {}", propertyName);
        List<Map<String, Object>> examples = new ArrayList<>();
        Schema itemSchema = CatsModelUtils.getSchemaItems(schema);

        List<Object> itemExamples = resolvePropertyToExamples(propertyName, itemSchema);
        for (Object itemExample : itemExamples) {
            Map<String, Object> newExample = new HashMap<>();
            int arraySize = getArrayLength(schema);

            Object toAdd = itemExample instanceof Map map && map.get(propertyName) != null ? map.get(propertyName) : itemExample;

            if (itemExample instanceof List<?> list) {
                toAdd = list.stream().map(innerObj -> {
                    if (innerObj instanceof Map map) {
                        return Optional.ofNullable(map.get(propertyName)).orElse(Optional.ofNullable(map.get(null)).orElse(innerObj));
                    }
                    return innerObj;
                }).toList();
            }

            newExample.put(propertyName, Collections.nCopies(arraySize, toAdd));
            examples.add(newExample);
        }

        return examples;
    }

    int getArrayLength(Schema<?> property) {
        int min = Optional.ofNullable(property.getMinItems()).orElse(1);

        int max = Optional.ofNullable(property.getMaxItems())
                .filter(maxVal -> maxVal != 0)
                .orElse(min + 1);

        logger.debug("Clamping array size between {} and {}, array size {}", min, max, this.maxArraySize);
        return Math.clamp(this.maxArraySize, min, max);
    }

    private List<Object> resolvePropertyToExamples(String propertyName, Schema property) {
        logger.trace("resolvePropertyToExamples for property {}", propertyName);
        List<Object> examples = new ArrayList<>();
        String previousProperty = currentProperty;

        currentProperty = StringUtils.isBlank(previousProperty) ? propertyName : previousProperty + "#" + propertyName;
        if (JsonUtils.isCyclicReference(currentProperty, selfReferenceDepth) || property == null) {
            currentProperty = previousProperty;
            return examples;
        }

        recordRequestSchema(currentProperty, property);

        if (CatsModelUtils.isArraySchema(property)) {
            Schema itemSchema = CatsModelUtils.getSchemaItems(property);
            List<Object> itemExamples = resolvePropertyToExamples(propertyName + ".items", itemSchema);
            int arraySize = getArrayLength(property);
            examples.addAll(itemExamples.stream().map(innerExample -> Collections.nCopies(arraySize, innerExample)).toList());
        } else if (CatsModelUtils.isMapSchema(property)) {
            Map<String, Object> mapExample = new HashMap<>();
            Schema additionalProperties = CatsModelUtils.getAdditionalProperties(property);
            List<Object> valueExamples = resolvePropertyToExamples("value", additionalProperties);
            for (Object valueExample : valueExamples) {
                mapExample.put("key", valueExample);
            }
            examples.add(mapExample);
        } else if (CatsModelUtils.isObjectSchemaUsingOAT(property) || !StringUtils.isEmpty(property.get$ref())) {
            examples.addAll(generateExamplesForSchema(propertyName, property));
        } else if (CatsModelUtils.isAllOf(property) || CatsModelUtils.isAllOfWithProperties(property)) {
            examples.addAll(resolveAllOfSchemaProperties(property, propertyName));
        } else if (CatsModelUtils.isOneOf(property) || CatsModelUtils.isAnyOf(property)) {
            examples.addAll(resolveAnyOfOneOfSchemaProperties(propertyName, property));
        } else {
            Object example = resolvePropertyToExample(propertyName, property);
            if (example != null) {
                examples.add(example);
            }
        }

        // Reset currentProperty
        currentProperty = previousProperty;

        // if examples are map and the key is null, just take out the values
        return examples;
    }

    private void mapDiscriminator(Schema<?> composedSchema, List<Schema> anyOf) {
        if (composedSchema.getDiscriminator() != null) {
            logger.trace("Mapping discriminator for schema {}", composedSchema.getName());
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

    private Object matchToEnumOrEmpty(String name, Schema innerSchema, String propertyName) {
        Schema resolveInnerSchema = Optional.ofNullable(globalContext.getSchemaFromReference(innerSchema.get$ref())).orElse(innerSchema);
        String result = Optional.ofNullable(resolveInnerSchema.getEnum())
                .orElse(List.of(""))
                .stream()
                .map(Object::toString)
                .filter(value -> name.toLowerCase(Locale.ROOT).contains(value.toString().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse("")
                .toString();
        if (result.isEmpty()) {
            return globalContext.getDiscriminators()
                    .stream()
                    .filter(discriminator -> discriminator.getPropertyName().equalsIgnoreCase(propertyName) && discriminator.getMapping() != null)
                    .findFirst()
                    .orElse(new Discriminator().mapping(Collections.emptyMap()))
                    .getMapping().keySet().stream().filter(key -> name.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElse("");
        }
        return result;
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

    private void mergeRequiredProperties(List<Schema> allOfSchema) {
        logger.trace("createMergedSchema for allOfSchema");
        Collection<Schema> allOfSchemasSanitized = allOfSchema
                .stream()
                .filter(CatsModelUtils::isNotEmptySchema)
                .map(schema -> schema.get$ref() != null ? globalContext.getSchemaFromReference(schema.get$ref()) : schema)
                .filter(Objects::nonNull)
                .toList();
        if (allOfSchemasSanitized.size() == 1) {
            return;
        }

        logger.trace("allOfSchemasSanitized size: {}", allOfSchemasSanitized.size());

        List<String> allRequired = allOfSchemasSanitized.stream()
                .map(schema -> Optional.ofNullable(schema.getRequired()).orElse(Collections.emptyList()))
                .flatMap(Collection::stream)
                .toList();

        logger.trace("allRequired size: {}", allRequired.size());

        for (Schema<?> schema : allOfSchemasSanitized) {
            if (schema.get$ref() != null) {
                schema = globalContext.getSchemaFromReference(schema.get$ref());
            }
            Set<String> propKeys = Optional.ofNullable(schema.getProperties()).orElse(Collections.emptyMap()).keySet();
            Schema<?> finalSchema = schema;

            allRequired.stream()
                    .filter(prop -> propKeys.contains(prop) && !Optional.ofNullable(finalSchema.getRequired())
                            .orElse(List.of())
                            .contains(prop))
                    .forEach(schema::addRequiredItem);
        }
    }

    private List<Map<String, Object>> combineExamples(List<Map<String, Object>> existingExamples, String propertyName, List<Object> propertyExamples) {
        logger.trace("combineExamples for property {}", propertyName);
        List<Map<String, Object>> combinedExamples = new ArrayList<>();

        if (existingExamples.isEmpty()) {
            for (Object propertyExample : propertyExamples) {
                Map<String, Object> newExample = new HashMap<>();
                addExampleAndKeepDepth(propertyName, propertyExample, newExample, combinedExamples);
            }
        } else {
            for (Map<String, Object> existingExample : existingExamples) {
                for (Object propertyExample : propertyExamples) {
                    Map<String, Object> newExample = new HashMap<>(existingExample);
                    addExampleAndKeepDepth(propertyName, propertyExample, newExample, combinedExamples);
                }
            }
        }
//        Collections.shuffle(combinedExamples);
        return combinedExamples.stream()
                .limit(Math.min(combinedExamples.size(), LIMIT_OF_EXAMPLES))
                .toList();
    }

    public Object extractExampleFromSchema(Schema<?> schema, boolean flagEnabled) {
        if (schema == null) {
            return null;
        }
        logger.trace("extractExampleFromSchema for schema {}, flag {}", schema.getName(), flagEnabled);
        if (flagEnabled) {
            Object result = schema.getExample();

            if (result == null && schema.getExamples() != null && !schema.getExamples().isEmpty()) {
                result = schema.getExamples().getFirst();
            }

            //check if this is a ref to the #/components/example section or a ref to another schema
            if (Objects.toString(result).contains("\"$ref\"")) {
                String refValue = String.valueOf(JsonUtils.getVariableFromJson(Objects.toString(result), "$ref"));
                if (refValue != null) {
                    result = globalContext.getObjectFromPathsReference(refValue);
                }
            }

            return formatExampleIfNeeded(schema, result);
        }
        return null;
    }

    private <T> Object generateStringValue(String propertyName, Schema<T> propertySchema) {
        if (propertyName == null || syntheticSchema(propertyName) || !CatsModelUtils.isStringSchema(propertySchema)) {
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

    <T> Object formatExampleIfNeeded(Schema<T> property, Object example) {
        if (example == null) {
            return null;
        }
        if (CatsModelUtils.isDateSchema(property)) {
            Date date = JsonUtils.getSimpleObjectMapper().convertValue(example, Date.class);
            return DATE_FORMATTER.format(LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault()));
        }
        if (CatsModelUtils.isDateTimeSchema(property)) {
            OffsetDateTime dateTime = JsonUtils.getSimpleObjectMapper().convertValue(example, OffsetDateTime.class);
            return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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

    private List<Map<String, Object>> combineExampleLists(List<Map<String, Object>> list1, List<Map<String, Object>> list2) {
        logger.trace("combineExampleLists");
        if (list1.isEmpty()) {
            return list2;
        }
        if (list2.isEmpty()) {
            return list1;
        }

        List<Map<String, Object>> combined = new ArrayList<>();
        for (Map<String, Object> example1 : list1) {
            for (Map<String, Object> example2 : list2) {
                Map<String, Object> combinedExample = new HashMap<>(example1);
                combinedExample.putAll(example2);
                combined.add(combinedExample);
            }
        }
        return combined;
    }

    private Object resolvePropertyToExample(String propertyName, Schema propertySchema) {
        logger.trace("resolvePropertyToExample for property {}", propertyName);
        //examples will take first priority
        Object example = this.extractExampleFromSchema(propertySchema, examplesFlags.usePropertyExamples());
        if (example != null) {
            logger.trace("Example set in swagger spec, returning example: '{}'", example);
            return example;
        }

        Object enumOrDefault = this.getEnumOrDefault(propertySchema);
        if (enumOrDefault != null) {
            return enumOrDefault;
        }

        Object generatedValueFromFormat = this.generateStringValue(propertyName, propertySchema);
        if (generatedValueFromFormat != null) {
            return generatedValueFromFormat;
        }

        return generateExampleBySchemaType(propertyName, propertySchema);
    }

    private <T> Object generateExampleBySchemaType(String propertyName, Schema<T> propertySchema) {
        if (CatsModelUtils.isStringSchema(propertySchema)) {
            return this.getExampleFromStringSchema(propertyName, propertySchema);
        } else if (CatsModelUtils.isBooleanSchema(propertySchema)) {
            return this.getExampleFromBooleanSchema();
        } else if (CatsModelUtils.isNumberSchema(propertySchema)) {
            return this.getExampleFromNumberSchema(propertySchema);
        } else if (CatsModelUtils.isIntegerSchema(propertySchema)) {
            return this.getExampleFromIntegerSchema(propertySchema);
        } else if (CatsModelUtils.isObjectSchema(propertySchema)) {
            return this.getExampleForObjectSchema(propertySchema);
        } else if (propertySchema.getAdditionalProperties() instanceof Schema) {
            return this.getExampleFromAdditionalPropertiesSchema(propertyName, propertySchema);
        }

        return null;
    }

    private <T> Object getExampleForObjectSchema(Schema<T> property) {
        return extractExampleFromSchema(property, examplesFlags.usePropertyExamples());
    }

    private Object getExampleFromBooleanSchema() {
        return random.nextBoolean();
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

    Map<String, Object> getExampleFromAdditionalPropertiesSchema(String propertyName, Schema property) {
        logger.trace("getExampleFromAdditionalPropertiesSchema for property {}", propertyName);
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

    private boolean hasValidRef(Schema schema) {
        if (schema.get$ref() != null) {
            Schema resolved = globalContext.getSchemaFromReference(schema.get$ref());
            return resolved != null;
        }
        return false;
    }

    private Map<String, Object> mergeMaps(Map<String, Object> firstMap, Map<String, Object> secondMap) {
        Map<String, Object> newMap = new HashMap<>();
        newMap.putAll(firstMap);
        newMap.putAll(secondMap);

        return newMap;
    }

    private void recordRequestSchema(String propertyName, Schema<?> schema) {
        if (schema == null) {
            return;
        }
        requestDataTypes.put(propertyName, schema);
        //this is a bit of a hack that might be abused in the future to include a full object as extension. currently it only holds the field name
        schema.addExtension(CatsModelUtils.X_CATS_FIELD_NAME, propertyName);
        if (schema.getDiscriminator() != null) {
            globalContext.getDiscriminators().add(schema.getDiscriminator());
        }
    }

    /**
     * Tries to execute a given supplier and records any exceptions thrown in the global context.
     *
     * @param toExecute    the supplier to execute
     * @param propertyName the name of the property to be used for recording exceptions
     * @param pattern      the pattern to be used for recording exceptions and that failed to generate a valid string
     * @return the result of the supplier execution, or null if an exception is thrown
     */
    public String generateAndRecordIfExceptionThrown(String propertyName, String pattern, Supplier<String> toExecute) {
        try {
            return toExecute.get();
        } catch (Exception e) {
            globalContext.recordError("A valid string could not be generated for the property '" + propertyName + "' using the pattern '" + pattern + "'. Please consider either changing the pattern or simplifying it.");
            return DEFAULT_STRING_WHEN_GENERATION_FAILS;
        }
    }
}