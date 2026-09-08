package com.endava.cats.openapi;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
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
    public static final int REQUEST_TOTAL_DEPTH = 200;
    public static final int RESPONSE_TOTAL_DEPTH = 50;
    private static final int MAX_DISTINCT_ARRAY_ITEM_ATTEMPTS = 20;
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(OpenAPIModelGeneratorV2.class);
    private final Random random;
    private final ProcessingArguments.ExamplesFlags examplesFlags;
    private final CatsGlobalContext globalContext;
    private final ValidDataFormat validDataFormat;
    private final int selfReferenceDepth;
    private final Map<String, Integer> callStackCounter;
    private final boolean useDefaults;
    private final int maxArraySize;
    private final Map<String, List<GeneratedExample>> examplesCache = new HashMap<>();

    @Getter
    private final Map<String, Schema> requestDataTypes = new HashMap<>();

    private String currentProperty = "";
    private final boolean resolveAnyOfAsMultipleSchema;
    private int currentPropertiesDepth;
    private final int totalDepth;
    private final String discriminatorCasing;
    private String currentRequiredProperty = "";
    private boolean bypassExamplesCache;
    private boolean bypassDeclaredExamples;

    /**
     * Constructs an OpenAPIModelGeneratorV2 with the specified configuration.
     * The default value for {@code resolveAnyOfAsMultipleSchema=true}. The default value for {@code totalDepth=200}.
     *
     * @param catsGlobalContext   The global context for CATS.
     * @param validDataFormat     The format to use for generating valid data.
     * @param useExamplesArgument Flag indicating whether to use examples from the OpenAPI specification.
     * @param selfReferenceDepth  The maximum depth for generating self-referencing models.
     * @param useDefaults         Whether to use default values if available
     * @param maxArraySize        The maximum size for arrays
     */
    public OpenAPIModelGeneratorV2(CatsGlobalContext catsGlobalContext, ValidDataFormat validDataFormat, ProcessingArguments.ExamplesFlags useExamplesArgument, int selfReferenceDepth, boolean useDefaults, int maxArraySize) {
        this(catsGlobalContext, validDataFormat, useExamplesArgument, selfReferenceDepth, useDefaults, maxArraySize, "UPPER_SNAKE_CASE");
    }

    /**
     * Constructs an OpenAPIModelGeneratorV2 with the specified configuration.
     * The default value for {@code resolveAnyOfAsMultipleSchema=true}. The default value for {@code totalDepth=200}.
     *
     * @param catsGlobalContext   The global context for CATS.
     * @param validDataFormat     The format to use for generating valid data.
     * @param useExamplesArgument Flag indicating whether to use examples from the OpenAPI specification.
     * @param selfReferenceDepth  The maximum depth for generating self-referencing models.
     * @param useDefaults         Whether to use default values if available
     * @param maxArraySize        The maximum size for arrays
     * @param discriminatorCasing The casing convention for discriminator values
     */
    public OpenAPIModelGeneratorV2(CatsGlobalContext catsGlobalContext, ValidDataFormat validDataFormat, ProcessingArguments.ExamplesFlags useExamplesArgument, int selfReferenceDepth, boolean useDefaults, int maxArraySize, String discriminatorCasing) {
        this.globalContext = catsGlobalContext;
        this.random = CatsRandom.instance();
        this.examplesFlags = useExamplesArgument;
        this.selfReferenceDepth = selfReferenceDepth;
        this.validDataFormat = validDataFormat;
        this.callStackCounter = new HashMap<>();
        this.useDefaults = useDefaults;
        this.maxArraySize = maxArraySize;
        this.discriminatorCasing = discriminatorCasing;

        this.resolveAnyOfAsMultipleSchema = true;
        this.totalDepth = REQUEST_TOTAL_DEPTH;
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
        this(catsGlobalContext, validDataFormat, useExamplesArgument, selfReferenceDepth, useDefaults, maxArraySize, resolveAnyOfAsMultipleSchema, "UPPER_SNAKE_CASE");
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
     * @param discriminatorCasing          The casing convention for discriminator values
     */
    public OpenAPIModelGeneratorV2(CatsGlobalContext catsGlobalContext, ValidDataFormat validDataFormat, ProcessingArguments.ExamplesFlags useExamplesArgument, int selfReferenceDepth, boolean useDefaults, int maxArraySize, boolean resolveAnyOfAsMultipleSchema, String discriminatorCasing) {
        this.globalContext = catsGlobalContext;
        this.random = CatsRandom.instance();
        this.examplesFlags = useExamplesArgument;
        this.selfReferenceDepth = selfReferenceDepth;
        this.validDataFormat = validDataFormat;
        this.callStackCounter = new HashMap<>();
        this.useDefaults = useDefaults;
        this.maxArraySize = maxArraySize;
        this.resolveAnyOfAsMultipleSchema = resolveAnyOfAsMultipleSchema;
        this.discriminatorCasing = discriminatorCasing;
        this.totalDepth = RESPONSE_TOTAL_DEPTH;
    }

    private void addExampleAndKeepDepth(String propertyName, GeneratedExample propertyExample, Map<String, Object> newExample,
                                        Set<String> requiredFields, List<GeneratedExample> combinedExamples) {
        logger.trace("addExampleAndKeepDepth for property {}", propertyName);
        switch (propertyExample.value()) {
            case Map map when map.get(null) != null -> newExample.put(propertyName, map.values().iterator().next());
            case Map map when map.get(propertyName) != null && isNotCyclingReference(propertyName) ->
                    newExample.put(propertyName, map.get(propertyName));
            case null, default -> newExample.put(propertyName, propertyExample.value());
        }
        requiredFields.addAll(propertyExample.requiredFields());
        combinedExamples.add(new GeneratedExample(newExample, requiredFields));
    }

    private boolean isNotCyclingReference(String propertyName) {
        return Arrays.stream(currentProperty.split("#", -1)).noneMatch(entry -> entry.equalsIgnoreCase(propertyName));
    }

    private static boolean isNullSchema(Schema innerType) {
        return (innerType.getType() == null || "null".equalsIgnoreCase(innerType.getType()))
                && innerType.get$ref() == null && !CatsModelUtils.isComposedSchema(innerType)
                && innerType.getProperties() == null && CatsUtil.isEmpty(innerType.getTypes());
    }


    public List<String> generate(String modelName) {
        return generateWithMetadata(modelName).stream().map(GeneratedPayload::payload).toList();
    }

    /**
     * Generates example payloads together with the required fields belonging to the exact schema variant that produced each payload.
     *
     * @param modelName the schema name
     * @return generated payloads and their variant-specific required fields
     */
    public List<GeneratedPayload> generateWithMetadata(String modelName) {
        List<GeneratedPayload> examples = new ArrayList<>();
        if (modelName != null) {
            final Schema schema = this.globalContext.getSchemaFromReference(modelName);
            if (schema != null) {
                List<GeneratedExample> generatedExamples = generateExamplesForSchema(modelName, schema);
                for (GeneratedExample generatedExample : generatedExamples) {
                    String example = this.transformInJson(modelName, generatedExample.value());

                    if (example != null) {
                        examples.add(new GeneratedPayload(example, List.copyOf(generatedExample.requiredFields())));
                    }
                }
                return examples.isEmpty() ? List.of(new GeneratedPayload("{}", List.of())) : examples;
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
            // If the generated example is a map of size 1, and it contains the model name as a key, we return the value of that key
            // If it has a bigger size, we merge the properties of the model with the additional properties. This happens when model uses allOf with one of the schemes
            // having additionalProperties i.e. MapSchema
            if (map.size() == 1) {
                generatedExample = map.get(modelName);
            } else {
                Map additionalProperties = (Map) map.get(modelName);
                map.remove(modelName);
                map.putAll(additionalProperties);
            }
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
        if (schema != null && schema.getDiscriminator() != null && !CatsUtil.isEmpty(schema.getDiscriminator().getMapping())
                && !CatsModelUtils.isComposedSchema(schema) && !isAnyComposedSchemaInChain(name)) {
            Schema<?> composedSchema = new Schema<>();
            composedSchema.setOneOf(schema.getDiscriminator().getMapping().values()
                    .stream()
                    .filter(schemaName -> !schemaName.equalsIgnoreCase(name))
                    .map(schemaName -> new Schema<>().$ref(schemaName))
                    .toList());
            composedSchema.setDiscriminator(schema.getDiscriminator());
            schema.getProperties().get(schema.getDiscriminator().getPropertyName()).setEnum(new ArrayList<>(schema.getDiscriminator().getMapping().keySet()));
            globalContext.recordDiscriminator(currentProperty, schema.getDiscriminator(), List.of());
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

    private List<GeneratedExample> getFromCacheOrExample(String cacheKey, Schema schema) {
        if (!bypassExamplesCache && examplesCache.containsKey(cacheKey)) {
            return examplesCache.get(cacheKey);
        }

        Object fromExample = extractExampleFromSchema(schema,
                examplesFlags.useSchemaExamples() && !bypassDeclaredExamples);
        if (fromExample != null) {
            return List.of(new GeneratedExample(formatExampleAsMap(fromExample),
                    collectRequiredFields(schema, currentRequiredProperty, Collections.newSetFromMap(new IdentityHashMap<>()))));
        }
        return List.of();
    }

    private List<GeneratedExample> generateExamplesForSchema(String name, Schema schema) {
        logger.trace("generateExamplesForSchema for schema {}", name);
        /* When checking for cyclic references make sure we exclude schema names. Some OpenAPI specs are generated by frameworks,
        so they might generate schema names that might seem cyclic such as: Body_Create_a_previously_generated_voice_v1_voice_generation_create_voice_post
         */
        if (JsonUtils.isCyclicReference(currentProperty, selfReferenceDepth) && globalContext.getSchemaFromReference(name) == null) {
            return List.of();
        }
        String cacheKey = currentRequiredProperty + "_" + name + "_" + schema.hashCode();

        List<GeneratedExample> fromCacheOrExample = getFromCacheOrExample(cacheKey, schema);
        if (!fromCacheOrExample.isEmpty()) {
            return fromCacheOrExample;
        }

        List<GeneratedExample> examples = new ArrayList<>();
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

        boolean hasOneOfOrAnyOf = CatsModelUtils.hasOneOf(schema) || CatsModelUtils.hasAnyOf(schema);
        boolean hasPropertiesAndComposed = (schema.getProperties() != null && !schema.getProperties().isEmpty()) && hasOneOfOrAnyOf;

        List<GeneratedExample> parentPropertyExamples = new ArrayList<>();
        if (hasPropertiesAndComposed) {
            parentPropertyExamples = traverseSchemaProperties(schema, name, true); // skip discriminator
        } else if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            examples.addAll(traverseSchemaProperties(schema, name));
        }

        if (CatsModelUtils.isAllOf(schema) || CatsModelUtils.isAllOfWithProperties(schema)) {
            List<GeneratedExample> allOfExamples = resolveAllOfSchemaProperties(schema, name);
            examples = combineExampleLists(examples, allOfExamples);
        }

        if (hasOneOfOrAnyOf) {
            examples = handleAnyOrOneOf(name, schema, parentPropertyExamples, examples);
        }

        if (CatsModelUtils.isArraySchema(schema)) {
            List<GeneratedExample> arrayExamples = resolveArraySchemaProperties(name, schema);
            examples = combineExampleLists(examples, arrayExamples);
        }

        if (examples.isEmpty()) {
            Object resolvedExample = resolvePropertyToExample(name, schema);
            if (resolvedExample != null) {
                Map<String, Object> defaultExample = new HashMap<>();
                String key = name != null && name.endsWith(".items") ? name.substring(0, name.lastIndexOf('.')) : name;
                defaultExample.put(key, resolvedExample);
                examples.add(new GeneratedExample(defaultExample, Collections.emptySet()));
            }
        }

        if (!bypassExamplesCache) {
            examplesCache.put(cacheKey, examples);
        }

        return examples;
    }

    /**
     * Returns all required fields declared by a schema. This is used only when a request example replaces the generated payload and
     * therefore cannot be associated with one of the generated composition variants.
     *
     * @param modelName the schema name
     * @return required field paths, using {@code #} as the nesting separator
     */
    public List<String> getRequiredFields(String modelName) {
        Schema<?> schema = globalContext.getSchemaFromReference(modelName);
        if (schema == null) {
            return List.of();
        }
        return List.copyOf(collectRequiredFields(schema, "", Collections.newSetFromMap(new IdentityHashMap<>())));
    }

    private List<GeneratedExample> handleAnyOrOneOf(String name, Schema schema, List<GeneratedExample> parentPropertyExamples, List<GeneratedExample> examples) {
        List<GeneratedExample> oneOfAnyOfExamples = resolveAnyOfOneOfSchemaProperties(name, schema);

        if (!parentPropertyExamples.isEmpty()) {
            List<GeneratedExample> mergedExamples = new ArrayList<>();
            for (GeneratedExample oneOfExample : oneOfAnyOfExamples) {
                for (GeneratedExample parentExample : parentPropertyExamples) {
                    Map<String, Object> merged = new HashMap<>(asMap(oneOfExample));

                    for (Map.Entry<String, Object> entry : asMap(parentExample).entrySet()) {
                        merged.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                    mergedExamples.add(new GeneratedExample(merged, unionRequiredFields(oneOfExample, parentExample)));
                }
            }
            examples = combineExampleLists(examples, mergedExamples);
        } else {
            examples = combineExampleLists(examples, oneOfAnyOfExamples);
        }
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

    private List<GeneratedExample> traverseSchemaProperties(Schema schema, String currentSchemaName) {
        return traverseSchemaProperties(schema, currentSchemaName, false);
    }

    private List<GeneratedExample> traverseSchemaProperties(Schema schema, String currentSchemaName, boolean skipDiscriminator) {
        logger.trace("traverseSchemaProperties for schema {}, skipDiscriminator: {}", currentSchemaName, skipDiscriminator);
        List<GeneratedExample> examples = new ArrayList<>();

        Set<Map.Entry<String, Schema>> properties = schema.getProperties().entrySet();
        Set<String> requiredProperties = new HashSet<>(Optional.ofNullable(schema.getRequired()).orElse(Collections.emptyList()));
        for (Map.Entry<String, Schema> entry : properties) {
            String propertyName = entry.getKey();
            Schema property = entry.getValue();
            String requiredPropertyPath = appendRequiredProperty(currentRequiredProperty, propertyName);

            currentPropertiesDepth++;
            // this is a hack to avoid infinite recursion when the schema references itself and JsonUtils.isCyclicReference does not catch it.
            // json responses tend to have a lot of self-references, and they return more complex objects than request payloads
            if (currentPropertiesDepth > totalDepth) {
                continue;
            }

            // Skip discriminator property if requested (when parent has oneOf/anyOf)
            if (skipDiscriminator && schema.getDiscriminator() != null &&
                    schema.getDiscriminator().getPropertyName().equalsIgnoreCase(propertyName)) {
                currentPropertiesDepth--;
                continue;
            }

            List<GeneratedExample> propertyExamples;
            if (schema.getDiscriminator() != null && schema.getDiscriminator().getPropertyName().equalsIgnoreCase(propertyName)) {
                Object discriminatorExample = matchToEnumOrEmpty(currentSchemaName, property, propertyName);
                propertyExamples = List.of(GeneratedExample.of(discriminatorExample));
                globalContext.recordDiscriminator(currentProperty, schema.getDiscriminator(), List.of(discriminatorExample));
                recordRequestSchema(currentProperty + "#" + propertyName, property);
            } else {
                propertyExamples = resolvePropertyToExamples(propertyName, property);
            }

            if (!propertyExamples.isEmpty()) {
                examples = combineExamples(examples, propertyName, propertyExamples, requiredPropertyPath,
                        requiredProperties.contains(propertyName));
                currentPropertiesDepth--;
            }
        }

        return examples;
    }

    private List<GeneratedExample> resolveAllOfSchemaProperties(Schema schema, String propertyName) {
        logger.trace("resolveAllOfSchemaProperties for schema {}", propertyName);
        List<GeneratedExample> examples = new ArrayList<>();
        keepOriginalSchema(propertyName, schema);
        mergeRequiredProperties(schema.getAllOf());

        // Check if any parent schema in allOf has a discriminator
        String discriminatorPropertyName = null;
        for (Object allOfSchemaObj : schema.getAllOf()) {
            Schema allOfSchema = (Schema) allOfSchemaObj;
            Schema resolvedSchema = allOfSchema.get$ref() != null
                    ? globalContext.getSchemaFromReference(allOfSchema.get$ref())
                    : allOfSchema;
            if (resolvedSchema != null && resolvedSchema.getDiscriminator() != null) {
                discriminatorPropertyName = resolvedSchema.getDiscriminator().getPropertyName();
                break;
            }
        }

        List<Schema> schemas = schema.getAllOf().stream().filter(iteratingSchema -> !isNullSchema((Schema<?>) iteratingSchema)).toList();
        for (Schema subSchema : schemas) {
            List<GeneratedExample> subExamples = generateExamplesForSchema(propertyName, subSchema);
            List<GeneratedExample> interimExamples = new ArrayList<>();

            if (CatsModelUtils.isAnyOf(subSchema) || CatsModelUtils.isOneOf(subSchema)) {
                interimExamples.addAll(subExamples);
            } else if (examples.isEmpty()) {
                examples.addAll(subExamples);
            } else {
                for (GeneratedExample subExample : subExamples) {
                    for (GeneratedExample existingExample : examples) {
                        interimExamples.add(mergeExamples(existingExample, subExample));
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

        // If a discriminator was found, ensure it's populated with the correct value
        if (discriminatorPropertyName != null && !examples.isEmpty()) {
            // Detect casing convention and convert schema name accordingly
            Schema parentWithDiscriminator = null;
            for (Object allOfSchemaObj : schema.getAllOf()) {
                Schema allOfSchema = (Schema) allOfSchemaObj;
                Schema resolvedSchema = allOfSchema.get$ref() != null
                        ? globalContext.getSchemaFromReference(allOfSchema.get$ref())
                        : allOfSchema;
                if (resolvedSchema != null && resolvedSchema.getDiscriminator() != null) {
                    parentWithDiscriminator = resolvedSchema;
                    break;
                }
            }

            String detectedCasing = parentWithDiscriminator != null
                    ? detectCasingConvention(parentWithDiscriminator)
                    : this.discriminatorCasing;
            String discriminatorValue = WordUtils.convertToDetectedCasing(propertyName, detectedCasing);

            for (GeneratedExample generatedExample : examples) {
                Map<String, Object> example = asMap(generatedExample);
                // Only set if not already set or if it's empty
                Object currentValue = example.get(discriminatorPropertyName);
                if (currentValue == null || currentValue.toString().isEmpty()) {
                    example.put(discriminatorPropertyName, discriminatorValue);
                }
            }
        }

        return examples;
    }


    private List<GeneratedExample> resolveAnyOfOneOfSchemaProperties(String propertyName, Schema<?> schema) {
        if (resolveAnyOfAsMultipleSchema) {
            return resolveAnyOfOneOfSchemaPropertiesWithMultipleSchemas(propertyName, schema);
        }
        return List.of(flatMap(resolveAnyOfOneOfSchemaPropertiesWithMultipleSchemas(propertyName, schema)));
    }

    private static GeneratedExample flatMap(List<GeneratedExample> generatedExamples) {
        Map<String, Object> flattened = new HashMap<>();
        Set<String> requiredFields = new LinkedHashSet<>();
        for (GeneratedExample generatedExample : generatedExamples) {
            requiredFields.addAll(generatedExample.requiredFields());
            for (Map.Entry<String, Object> entry : asMap(generatedExample).entrySet()) {
                flattened.merge(entry.getKey(), entry.getValue(), (existing, replacement) -> {
                    if (existing instanceof List<?> existingList) {
                        List<Object> values = new ArrayList<>(existingList);
                        values.add(replacement);
                        return values;
                    }
                    return new ArrayList<>(List.of(existing, replacement));
                });
            }
        }
        return new GeneratedExample(flattened, requiredFields);
    }

    private List<GeneratedExample> resolveAnyOfOneOfSchemaPropertiesWithMultipleSchemas(String propertyName, Schema<?> schema) {
        logger.trace("resolveAnyOfOneOfSchemaProperties for schema {}", propertyName);
        mapDiscriminator(schema, Optional.ofNullable(schema.getAnyOf()).orElse(schema.getOneOf()));
        List<GeneratedExample> examples = new ArrayList<>();
        List<Schema> schemas = CatsModelUtils.getInterfaces(schema).stream().filter(iteratingSchema -> !isNullSchema(iteratingSchema)).toList();

        for (Schema subSchema : schemas) {
            if (hasValidRef(subSchema)) {
                Schema resolved = globalContext.getSchemaFromReference(subSchema.get$ref());
                if (resolved != null) {
                    String schemaName = CatsModelUtils.getSimpleRefUsingOAT(subSchema.get$ref());
                    List<GeneratedExample> variantExamples = generateExamplesForSchema(schemaName, resolved);
                    for (GeneratedExample variantExample : variantExamples) {
                        Map<String, Object> wrappedExample = new HashMap<>();
                        Map<String, Object> variantValue = asMap(variantExample);
                        wrappedExample.put(propertyName, variantValue.getOrDefault(schemaName, variantValue));
                        examples.add(new GeneratedExample(wrappedExample, variantExample.requiredFields()));
                    }
                }
            } else {
                examples.addAll(generateExamplesForSchema(propertyName, subSchema));
            }
        }

        Set<String> composedRequiredFields = Optional.ofNullable(schema.getRequired()).orElse(Collections.emptyList()).stream()
                .map(required -> appendRequiredProperty(currentRequiredProperty, required))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (composedRequiredFields.isEmpty()) {
            return examples;
        }
        return examples.stream()
                .map(example -> {
                    Set<String> requiredFields = new LinkedHashSet<>(example.requiredFields());
                    requiredFields.addAll(composedRequiredFields);
                    return new GeneratedExample(example.value(), requiredFields);
                })
                .toList();
    }

    private static Schema getArrayItemsOrDefault(Schema schema) {
        Schema items = schema.getItems();
        if (items == null) {
            return new Schema<>().type("string").description("TODO default missing array inner type to string");
        }
        return items;
    }

    private List<GeneratedExample> resolveArraySchemaProperties(String propertyName, Schema schema) {
        logger.trace("resolveArraySchemaProperties for schema {}", propertyName);
        List<GeneratedExample> examples = new ArrayList<>();
        Schema itemSchema = getArrayItemsOrDefault(schema);
        int arraySize = getArrayLength(schema);
        List<GeneratedExample> arrayExamples = generateArrayExamples(propertyName, itemSchema, arraySize,
                item -> unwrapArrayItem(propertyName, item), Boolean.TRUE.equals(schema.getUniqueItems()));

        for (GeneratedExample arrayExample : arrayExamples) {
            Map<String, Object> newExample = new HashMap<>();
            newExample.put(propertyName, arrayExample.value());
            examples.add(new GeneratedExample(newExample, arrayExample.requiredFields()));
        }

        return examples;
    }

    private Object unwrapArrayItem(String propertyName, Object itemExample) {
        Object toAdd = itemExample;
        if (itemExample instanceof Map map) {
            // Attempt to unwrap primitive value
            Object value = map.get(propertyName);
            if (value == null && map.size() == 1) {
                value = map.values().iterator().next(); // fallback for primitive map
            }
            toAdd = value != null ? value : itemExample;
        }

        if (itemExample instanceof List<?> list) {
            toAdd = list.stream().map(innerObj -> {
                if (innerObj instanceof Map map) {
                    return Optional.ofNullable(map.get(propertyName)).orElse(Optional.ofNullable(map.get(null)).orElse(innerObj));
                }
                return innerObj;
            }).toList();
        }

        return toAdd;
    }

    int getArrayLength(Schema<?> property) {
        int min = Optional.ofNullable(property.getMinItems()).orElse(1);

        int maxFromEnum = Optional.ofNullable(Optional.ofNullable(property.getItems()).
                orElse(new Schema<>())
                .getEnum()).map(List::size).orElse(0);

        int max = maxFromEnum != 0 ? maxFromEnum : Optional.ofNullable(property.getMaxItems())
                .filter(maxVal -> maxVal != 0)
                .orElse(min + 1);

        logger.debug("Clamping array size between {} and {}, array size {}", min, max, this.maxArraySize);
        return Math.clamp(this.maxArraySize, min, max);
    }

    private List<GeneratedExample> resolvePropertyToExamples(String propertyName, Schema property) {
        return resolvePropertyToExamples(propertyName, property, false);
    }

    private List<GeneratedExample> resolvePropertyToExamples(String propertyName, Schema property, boolean arrayItem) {
        logger.trace("resolvePropertyToExamples for property {}", propertyName);
        List<GeneratedExample> examples = new ArrayList<>();
        String previousProperty = currentProperty;
        String previousRequiredProperty = currentRequiredProperty;

        currentProperty = StringUtils.isBlank(previousProperty) ? propertyName : previousProperty + "#" + propertyName;
        if (!arrayItem) {
            currentRequiredProperty = appendRequiredProperty(previousRequiredProperty, propertyName);
        }
        if (JsonUtils.isCyclicReference(currentProperty, selfReferenceDepth) || property == null) {
            currentProperty = previousProperty;
            currentRequiredProperty = previousRequiredProperty;
            return examples;
        }

        recordRequestSchema(currentProperty, property);

        if (CatsModelUtils.isArraySchema(property)) {
            createExamplesArray(propertyName, property, examples);
        } else if (CatsModelUtils.isMapSchema(property)) {
            Map<String, Object> mapExample = new HashMap<>();
            Schema additionalProperties = CatsModelUtils.getAdditionalProperties(property);
            List<GeneratedExample> valueExamples = resolvePropertyToExamples("value", additionalProperties);
            GeneratedExample lastValue = null;
            for (GeneratedExample valueExample : valueExamples) {
                mapExample.put("key", valueExample.value());
                lastValue = valueExample;
            }
            examples.add(new GeneratedExample(mapExample,
                    lastValue == null ? Collections.emptySet() : lastValue.requiredFields()));
        } else if (!StringUtils.isEmpty(property.get$ref())) {
            Schema<?> resolved = globalContext.getSchemaFromReference(property.get$ref());
            if (resolved != null && CatsModelUtils.isPrimitiveSchema(resolved)) {
                Object value = resolvePropertyToExample(propertyName, resolved);
                if (value != null) {
                    examples.add(GeneratedExample.of(value));
                }
            } else {
                examples.addAll(generateExamplesForSchema(propertyName, property));
            }
        } else if (CatsModelUtils.isObjectSchemaUsingOAT(property)) {
            examples.addAll(generateExamplesForSchema(propertyName, property));
        } else if (CatsModelUtils.isAllOf(property) || CatsModelUtils.isAllOfWithProperties(property)) {
            examples.addAll(resolveAllOfSchemaProperties(property, propertyName));
        } else if (CatsModelUtils.isOneOf(property) || CatsModelUtils.isAnyOf(property)) {
            examples.addAll(resolveAnyOfOneOfSchemaProperties(propertyName, property));
        } else {
            Object example = resolvePropertyToExample(propertyName, property);
            if (example != null) {
                examples.add(GeneratedExample.of(example));
            }
        }

        currentProperty = previousProperty;
        currentRequiredProperty = previousRequiredProperty;

        return examples;
    }

    private void createExamplesArray(String propertyName, Schema property, List<GeneratedExample> examples) {
        Object arrayExample = extractExampleFromSchema(property,
                examplesFlags.usePropertyExamples() && !bypassDeclaredExamples);
        if (arrayExample != null) {
            examples.add(new GeneratedExample(arrayExample,
                    collectRequiredFields(property, currentRequiredProperty, Collections.newSetFromMap(new IdentityHashMap<>()))));
            return;
        }

        Schema itemSchema = getArrayItemsOrDefault(property);
        int arraySize = getArrayLength(property);
        examples.addAll(generateArrayExamples(propertyName + ".items", itemSchema, arraySize,
                Function.identity(), Boolean.TRUE.equals(property.getUniqueItems())));
    }

    private List<GeneratedExample> generateArrayExamples(String propertyName, Schema itemSchema, int arraySize,
                                                          Function<Object, Object> itemMapper, boolean uniqueItems) {
        if (CatsModelUtils.isFreeFormSchema(itemSchema)) {
            List<Object> items = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < arraySize; itemIndex++) {
                items.add(getFreeFormArrayItem(itemIndex));
            }
            return List.of(new GeneratedExample(items, Collections.emptySet()));
        }

        List<GeneratedExample> templates = generateFreshArrayItemExamples(propertyName, itemSchema);
        List<GeneratedExample> arrays = new ArrayList<>();

        for (int variantIndex = 0; variantIndex < templates.size(); variantIndex++) {
            GeneratedExample template = templates.get(variantIndex);
            List<Object> items = new ArrayList<>();
            Set<String> requiredFields = new LinkedHashSet<>();

            for (int itemIndex = 0; itemIndex < arraySize; itemIndex++) {
                GeneratedExample itemExample = itemIndex == 0
                        ? template
                        : generateArrayItemVariant(propertyName, itemSchema, variantIndex, template, uniqueItems);
                Object item = itemMapper.apply(itemExample.value());

                int attempts = 0;
                while (uniqueItems && containsEquivalent(items, item)
                        && attempts++ < MAX_DISTINCT_ARRAY_ITEM_ATTEMPTS) {
                    itemExample = generateArrayItemVariant(propertyName, itemSchema, variantIndex, template, true);
                    item = itemMapper.apply(itemExample.value());
                }

                if (uniqueItems && containsEquivalent(items, item)) {
                    logger.debug("Could not generate {} unique items for array {}", arraySize, propertyName);
                    break;
                }

                items.add(item);
                requiredFields.addAll(itemExample.requiredFields());
            }
            arrays.add(new GeneratedExample(items, requiredFields));
        }

        return arrays;
    }

    private static Object getFreeFormArrayItem(int itemIndex) {
        return switch (itemIndex) {
            case 0 -> "cats";
            case 1 -> 42;
            case 2 -> true;
            case 3 -> Map.of("cats", "fuzzy");
            case 4 -> List.of("cats");
            case 5 -> null;
            default -> "cats" + itemIndex;
        };
    }

    private GeneratedExample generateArrayItemVariant(String propertyName, Schema itemSchema, int variantIndex,
                                                       GeneratedExample fallback, boolean ignoreDeclaredExamples) {
        List<GeneratedExample> generated = generateFreshArrayItemExamples(propertyName, itemSchema,
                ignoreDeclaredExamples);
        return variantIndex < generated.size() ? generated.get(variantIndex) : fallback;
    }

    private List<GeneratedExample> generateFreshArrayItemExamples(String propertyName, Schema itemSchema) {
        return generateFreshArrayItemExamples(propertyName, itemSchema, false);
    }

    private List<GeneratedExample> generateFreshArrayItemExamples(String propertyName, Schema itemSchema,
                                                                   boolean ignoreDeclaredExamples) {
        boolean previousBypassExamplesCache = bypassExamplesCache;
        boolean previousBypassDeclaredExamples = bypassDeclaredExamples;
        bypassExamplesCache = true;
        bypassDeclaredExamples = ignoreDeclaredExamples;
        try {
            return resolvePropertyToExamples(propertyName, itemSchema, true);
        } finally {
            bypassExamplesCache = previousBypassExamplesCache;
            bypassDeclaredExamples = previousBypassDeclaredExamples;
        }
    }

    private static boolean containsEquivalent(List<Object> items, Object candidate) {
        return items.stream().anyMatch(item -> Objects.deepEquals(item, candidate));
    }

    private void mapDiscriminator(Schema<?> composedSchema, List<Schema> anyOf) {
        if (composedSchema.getDiscriminator() != null) {
            logger.trace("Mapping discriminator for schema {}", composedSchema.getName());
            globalContext.recordDiscriminator(currentProperty, composedSchema.getDiscriminator(), List.of());
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

        // First, try to find the discriminator value using the mapping (schema name -> enum value)
        String resultFromMapping = globalContext.getDiscriminators()
                .stream()
                .filter(discriminator -> discriminator.getPropertyName().equalsIgnoreCase(propertyName) && discriminator.getMapping() != null)
                .findFirst()
                .map(discriminator ->
                        // Find the enum value that maps to this schema name
                        discriminator.getMapping().entrySet().stream()
                                .filter(entry -> {
                                    String schemaRef = entry.getValue();
                                    String schemaName = CatsModelUtils.getSimpleRef(schemaRef);
                                    return schemaName.equalsIgnoreCase(name);
                                })
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElse(""))
                .orElse("");

        if (!resultFromMapping.isEmpty()) {
            return resultFromMapping;
        }

        String result = Optional.ofNullable(resolveInnerSchema.getEnum())
                .orElse(List.of(""))
                .stream()
                .map(Object::toString)
                .filter(value -> name.toLowerCase(Locale.ROOT).contains(value.toString().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse("")
                .toString();
        if (result.isEmpty()) {
            result = globalContext.getDiscriminators()
                    .stream()
                    .filter(discriminator -> discriminator.getPropertyName().equalsIgnoreCase(propertyName) && discriminator.getMapping() != null)
                    .findFirst()
                    .orElse(new Discriminator().mapping(Collections.emptyMap()))
                    .getMapping().keySet().stream().filter(key -> name.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElse("");
        }

        // If still empty, try to infer from schema name (for allOf pattern without explicit mapping)
        // Detect casing convention from enum values or existing discriminator mappings
        if (result.isEmpty() && name != null) {
            String detectedCasing = detectCasingConvention(resolveInnerSchema);
            result = WordUtils.convertToDetectedCasing(name, detectedCasing);
        }


        return result;
    }

    private String detectCasingConvention(Schema schema) {
        // Try to detect from enum values first
        List<Object> enumValues = Optional.ofNullable(schema.getEnum()).orElse(List.of());
        if (!enumValues.isEmpty()) {
            String firstEnum = enumValues.getFirst().toString();
            return WordUtils.detectCasingFromString(firstEnum);
        }

        // Try to detect from the schema's own discriminator mapping only
        if (schema.getDiscriminator() != null && schema.getDiscriminator().getMapping() != null) {
            Optional<String> mappingKey = schema.getDiscriminator().getMapping().keySet().stream().findFirst();
            if (mappingKey.isPresent()) {
                return WordUtils.detectCasingFromString(mappingKey.get());
            }
        }

        return this.discriminatorCasing;
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

    private List<GeneratedExample> combineExamples(List<GeneratedExample> existingExamples, String propertyName,
                                                   List<GeneratedExample> propertyExamples, String requiredPropertyPath,
                                                   boolean required) {
        logger.trace("combineExamples for property {}", propertyName);
        List<GeneratedExample> combinedExamples = new ArrayList<>();

        if (existingExamples.isEmpty()) {
            for (GeneratedExample propertyExample : propertyExamples) {
                Map<String, Object> newExample = new HashMap<>();
                Set<String> requiredFields = new LinkedHashSet<>();
                if (required) {
                    requiredFields.add(requiredPropertyPath);
                }
                addExampleAndKeepDepth(propertyName, propertyExample, newExample, requiredFields, combinedExamples);
            }
        } else {
            for (GeneratedExample existingExample : existingExamples) {
                for (GeneratedExample propertyExample : propertyExamples) {
                    Map<String, Object> newExample = new HashMap<>(asMap(existingExample));
                    Set<String> requiredFields = new LinkedHashSet<>(existingExample.requiredFields());
                    if (required) {
                        requiredFields.add(requiredPropertyPath);
                    }
                    addExampleAndKeepDepth(propertyName, propertyExample, newExample, requiredFields, combinedExamples);
                }
            }
        }

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

        if (!CatsUtil.isEmpty(enumValues)) {
            List<T> nonNullEnumValues = enumValues.stream()
                    .filter(Objects::nonNull)
                    .toList();

            if (!nonNullEnumValues.isEmpty()) {
                return nonNullEnumValues.get(CatsRandom.instance().nextInt(nonNullEnumValues.size()));
            }

            return enumValues.getFirst(); // fallback if all were null
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
            } catch (IllegalArgumentException _) {
                return example;
            }
        }
        if (String.valueOf(example).contains("\n")) {
            return String.valueOf(example).replace("\n", "");
        }
        return example;
    }

    private List<GeneratedExample> combineExampleLists(List<GeneratedExample> list1, List<GeneratedExample> list2) {
        logger.trace("combineExampleLists");
        if (list1.isEmpty()) {
            return list2;
        }
        if (list2.isEmpty()) {
            return list1;
        }

        List<GeneratedExample> combined = new ArrayList<>();
        for (GeneratedExample example1 : list1) {
            for (GeneratedExample example2 : list2) {
                Map<String, Object> combinedExample = new HashMap<>(asMap(example1));
                combinedExample.putAll(asMap(example2));
                combined.add(new GeneratedExample(combinedExample, unionRequiredFields(example1, example2)));
            }
        }
        return combined;
    }

    private Object resolvePropertyToExample(String propertyName, Schema propertySchema, boolean useExamples) {
        logger.trace("resolvePropertyToExample for property {}", propertyName);
        //examples will take first priority
        Object example = this.extractExampleFromSchema(propertySchema, useExamples && !bypassDeclaredExamples);
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

    private Object resolvePropertyToExample(String propertyName, Schema propertySchema) {
        return resolvePropertyToExample(propertyName, propertySchema, examplesFlags.usePropertyExamples());
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
        return extractExampleFromSchema(property, examplesFlags.usePropertyExamples() && !bypassDeclaredExamples);
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
            if (range == 0.0) {
                return min;
            }
            return random.nextDouble() * range + min;
        } else if (min != null) {
            return random.nextDouble() * 100000.0 + min;
        } else if (max != null) {
            if (max == 0.0) {
                return 0.0;
            }
            return random.nextDouble() * max;
        } else {
            return random.nextDouble() * 100000.0;
        }
    }

    private boolean hasValidRef(Schema schema) {
        if (schema.get$ref() != null) {
            Schema resolved = globalContext.getSchemaFromReference(schema.get$ref());
            return resolved != null;
        }
        return false;
    }

    private static GeneratedExample mergeExamples(GeneratedExample first, GeneratedExample second) {
        Map<String, Object> merged = new HashMap<>(asMap(first));
        merged.putAll(asMap(second));
        return new GeneratedExample(merged, unionRequiredFields(first, second));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(GeneratedExample example) {
        return (Map<String, Object>) example.value();
    }

    private static Set<String> unionRequiredFields(GeneratedExample... examples) {
        Set<String> requiredFields = new LinkedHashSet<>();
        for (GeneratedExample example : examples) {
            requiredFields.addAll(example.requiredFields());
        }
        return requiredFields;
    }

    private static String appendRequiredProperty(String parent, String property) {
        return StringUtils.isBlank(parent) ? property : parent + "#" + property;
    }

    private Set<String> collectRequiredFields(Schema<?> schema, String parent, Set<Schema<?>> visited) {
        if (schema == null) {
            return new LinkedHashSet<>();
        }
        if (schema.get$ref() != null) {
            schema = globalContext.getSchemaFromReference(schema.get$ref());
            if (schema == null) {
                return new LinkedHashSet<>();
            }
        }
        if (!visited.add(schema)) {
            return new LinkedHashSet<>();
        }

        Set<String> requiredFields = new LinkedHashSet<>();
        Set<String> requiredProperties = new HashSet<>(Optional.ofNullable(schema.getRequired()).orElse(Collections.emptyList()));
        Map<String, Schema> properties = Optional.ofNullable(schema.getProperties()).orElse(Collections.emptyMap());
        for (Map.Entry<String, Schema> property : properties.entrySet()) {
            String propertyPath = appendRequiredProperty(parent, property.getKey());
            if (requiredProperties.contains(property.getKey())) {
                requiredFields.add(propertyPath);
            }
            requiredFields.addAll(collectRequiredFields(property.getValue(), propertyPath, visited));
        }

        if (schema.getItems() != null) {
            requiredFields.addAll(collectRequiredFields(schema.getItems(), parent, visited));
        }
        for (Schema<?> composedSchema : CatsModelUtils.getInterfaces(schema)) {
            requiredFields.addAll(collectRequiredFields(composedSchema, parent, visited));
        }
        visited.remove(schema);
        return requiredFields;
    }

    private void recordRequestSchema(String propertyName, Schema<?> schema) {
        if (schema == null) {
            return;
        }
        requestDataTypes.put(propertyName, schema);
        //this is a bit of a hack that might be abused in the future to include a full object as extension. currently it only holds the field name
        schema.addExtension(CatsModelUtils.X_CATS_FIELD_NAME, propertyName);
        if (schema.getDiscriminator() != null) {
            globalContext.recordDiscriminator(currentProperty, schema.getDiscriminator(), List.of());
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
        } catch (Exception _) {
            globalContext.recordError("A valid string could not be generated for the property '" + propertyName + "' using the pattern '" + pattern + "'. Please consider either changing the pattern or simplifying it.");
            return DEFAULT_STRING_WHEN_GENERATION_FAILS;
        }
    }

    private record GeneratedExample(Object value, Set<String> requiredFields) {
        private GeneratedExample {
            requiredFields = Collections.unmodifiableSet(new LinkedHashSet<>(requiredFields));
        }

        private static GeneratedExample of(Object value) {
            return new GeneratedExample(value, Collections.emptySet());
        }
    }

    /**
     * A generated JSON payload and the required fields for the exact schema composition variant used to create it.
     *
     * @param payload        generated JSON payload
     * @param requiredFields required field paths, using {@code #} as the nesting separator
     */
    public record GeneratedPayload(String payload, List<String> requiredFields) {
        public GeneratedPayload {
            requiredFields = List.copyOf(requiredFields);
        }
    }
}
