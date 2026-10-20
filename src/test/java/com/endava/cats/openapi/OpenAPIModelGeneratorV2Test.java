package com.endava.cats.openapi;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.google.gson.JsonElement;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

@QuarkusTest
class OpenAPIModelGeneratorV2Test {

    @Inject
    CatsGlobalContext globalContext;
    @Inject
    ValidDataFormat validDataFormat;
    @Inject
    ProcessingArguments processingArguments;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        globalContext.getSchemaMap().clear();
        globalContext.getDiscriminators().clear();
    }

    @Test
    void givenASimpleOpenAPIContract_whenGeneratingAPayload_thenTheExampleIsProperlyGenerated() throws Exception {
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();

        List<String> example = generator.generate("Pet");

        Assertions.assertThat(example).hasSize(2);

        Assertions.assertThat(example.stream().filter(e -> e.contains("Husky"))).hasSize(1);
        Assertions.assertThat(example.stream().filter(e -> e.contains("Labrador"))).hasSize(1);
    }

    @Test
    void shouldGenerateOneOfWhenOneOfInRoot() throws Exception {
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();

        List<String> example = generator.generate("PetType");

        Assertions.assertThat(example).hasSize(2);
        Assertions.assertThat(example.getFirst()).contains("Husky");
        Assertions.assertThat(example.getLast()).contains("Labrador");
    }

    @Test
    void shouldGenerateFromAllOfAndPreserveRootElement() throws Exception {
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        List<String> example = generator.generate("MiniPet");
        String exampleJson = example.getFirst();

        Assertions.assertThat(exampleJson).contains("color").contains("red").contains("green").contains("blue");
    }

    @ParameterizedTest
    @CsvSource({"MegaPet", "AdditionalPet", "ObjectPet"})
    void shouldGenerateAdditionalPropertiesWhenInlineDef(String modelName) throws Exception {
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        List<String> example = generator.generate(modelName);
        String exampleJson = example.getFirst();

        Assertions.assertThat(exampleJson).contains("metadata");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "metadata#key")).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyGenerateWhenCyclicReference() throws Exception {
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        List<String> example = generator.generate("CyclicPet");
        String exampleJson = example.getFirst();

        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#parent#parent#code")).isNotEqualTo("NOT_SET");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#parent#parent#parent#code")).isEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyFormatDateExamples() throws Exception {
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        List<String> example = generator.generate("MegaPet");
        String exampleJson = example.getFirst();

        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#dateOfBirth")).asString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#timeOfVaccination")).asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{1,16}Z");
    }

    @ParameterizedTest
    @CsvSource(value = {"null,1,1", "0,1,1", "null,null,2", "1,null,2", "2,3,2"}, nullValues = "null")
    void shouldComputeProperArrayLength(Integer min, Integer max, int expected) throws Exception {
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();

        Schema<?> schema = new Schema<>();
        schema.setMinItems(min);
        schema.setMaxItems(max);
        int result = generator.getArrayLength(schema);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldGenerateDifferentObjectsForEachNestedArrayElement() {
        ValidDataFormat sequentialDataFormat = Mockito.mock(ValidDataFormat.class);
        Mockito.when(sequentialDataFormat.generate(Mockito.any(), Mockito.anyString()))
                .thenReturn("first", "second");

        ObjectSchema itemSchema = new ObjectSchema();
        itemSchema.addProperty("name", new StringSchema());
        ArraySchema arraySchema = new ArraySchema();
        arraySchema.setItems(itemSchema);
        arraySchema.setMinItems(2);
        arraySchema.setMaxItems(2);
        ObjectSchema containerSchema = new ObjectSchema();
        containerSchema.addProperty("items", arraySchema);
        globalContext.getSchemaMap().put("ArrayContainer", containerSchema);

        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, sequentialDataFormat,
                new ProcessingArguments.ExamplesFlags(false, false, false, false), 3, true, 2);

        String payload = generator.generate("ArrayContainer").getFirst();

        var items = JsonUtils.parseAsJsonElement(payload).getAsJsonObject().getAsJsonArray("items");
        Assertions.assertThat(items).hasSize(2);
        Assertions.assertThat(items.get(0).getAsJsonObject().get("name").getAsString()).isEqualTo("first");
        Assertions.assertThat(items.get(1).getAsJsonObject().get("name").getAsString()).isEqualTo("second");
    }

    @Test
    void shouldGenerateDifferentValuesForEachRootArrayElement() {
        ValidDataFormat sequentialDataFormat = Mockito.mock(ValidDataFormat.class);
        Mockito.when(sequentialDataFormat.generate(Mockito.any(), Mockito.anyString()))
                .thenReturn("first", "second");

        ArraySchema rootArraySchema = new ArraySchema();
        rootArraySchema.setItems(new StringSchema());
        rootArraySchema.setMinItems(2);
        rootArraySchema.setMaxItems(2);
        globalContext.getSchemaMap().put("RootArray", rootArraySchema);
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, sequentialDataFormat,
                new ProcessingArguments.ExamplesFlags(false, false, false, false), 3, true, 2);

        String payload = generator.generate("RootArray").getFirst();

        var items = JsonUtils.parseAsJsonElement(payload).getAsJsonArray();
        Assertions.assertThat(items).hasSize(2);
        Assertions.assertThat(items.get(0).getAsString()).isEqualTo("first");
        Assertions.assertThat(items.get(1).getAsString()).isEqualTo("second");
    }

    @Test
    void shouldGenerateMixedValuesForFreeFormArrayItems() {
        ArraySchema rootArraySchema = new ArraySchema();
        rootArraySchema.setItems(new Schema<>());
        rootArraySchema.setMinItems(6);
        rootArraySchema.setMaxItems(6);
        globalContext.getSchemaMap().put("FreeFormArray", rootArraySchema);
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat,
                new ProcessingArguments.ExamplesFlags(false, false, false, false), 3, true, 6);

        String payload = generator.generate("FreeFormArray").getFirst();

        var items = JsonUtils.parseAsJsonElement(payload).getAsJsonArray();
        Assertions.assertThat(items).hasSize(6);
        Assertions.assertThat(items.get(0).isJsonPrimitive()).isTrue();
        Assertions.assertThat(items.get(0).getAsString()).isEqualTo("cats");
        Assertions.assertThat(items.get(1).getAsInt()).isEqualTo(42);
        Assertions.assertThat(items.get(2).getAsBoolean()).isTrue();
        Assertions.assertThat(items.get(3).isJsonObject()).isTrue();
        Assertions.assertThat(items.get(4).isJsonArray()).isTrue();
        Assertions.assertThat(items.get(5).isJsonNull()).isTrue();
    }

    @Test
    void shouldRetainRepeatedValuesWhenTheItemExampleIsFixed() {
        StringSchema itemSchema = new StringSchema();
        itemSchema.setExample("fixed");
        ArraySchema rootArraySchema = new ArraySchema();
        rootArraySchema.setItems(itemSchema);
        rootArraySchema.setMinItems(2);
        rootArraySchema.setMaxItems(2);
        globalContext.getSchemaMap().put("RootArray", rootArraySchema);
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat,
                new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);

        String payload = generator.generate("RootArray").getFirst();

        var items = JsonUtils.parseAsJsonElement(payload).getAsJsonArray();
        Assertions.assertThat(items.asList().stream().map(JsonElement::getAsString).toList())
                .containsExactly("fixed", "fixed");
    }

    @Test
    void shouldGenerateAnAlternativeToAFixedExampleForUniqueItems() {
        ValidDataFormat sequentialDataFormat = Mockito.mock(ValidDataFormat.class);
        Mockito.when(sequentialDataFormat.generate(Mockito.any(), Mockito.anyString())).thenReturn("generated");

        StringSchema itemSchema = new StringSchema();
        itemSchema.setExample("fixed");
        ArraySchema rootArraySchema = new ArraySchema();
        rootArraySchema.setItems(itemSchema);
        rootArraySchema.setMinItems(2);
        rootArraySchema.setMaxItems(2);
        rootArraySchema.setUniqueItems(true);
        globalContext.getSchemaMap().put("RootArray", rootArraySchema);
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, sequentialDataFormat,
                new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);

        String payload = generator.generate("RootArray").getFirst();

        var items = JsonUtils.parseAsJsonElement(payload).getAsJsonArray();
        Assertions.assertThat(items).hasSize(2);
        Assertions.assertThat(items.asList().stream().map(JsonElement::getAsString).toList())
                .containsExactly("fixed", "generated");
    }

    @Test
    void shouldFormatDateExample() throws Exception {
        Schema<Date> schema = new Schema<>();
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        schema.setExample(Date.from(Instant.parse("2020-01-02T00:00:00Z")));
        schema.setType("string");
        schema.setFormat("date");

        Object formatted = generator.extractExampleFromSchema(schema, true);

        Assertions.assertThat(formatted).hasToString("2020-01-02");
    }

    @Test
    void shouldRemoveNewLine() throws Exception {
        Schema<String> schema = new Schema<>();
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        schema.setType("string");
        schema.setExample("This is a string\nwith a new line");

        Object formatted = generator.extractExampleFromSchema(schema, true);

        Assertions.assertThat(formatted).hasToString("This is a stringwith a new line");
    }

    @Test
    void shouldFormatDateTimeExample() throws Exception {
        Schema<Date> schema = new Schema<>();
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        schema.setExample(OffsetDateTime.of(2020, 1, 2, 0, 0, 0, 0, OffsetDateTime.now(ZoneId.of("UTC")).getOffset()));
        schema.setType("string");
        schema.setFormat("date-time");

        Object generatedExample = generator.extractExampleFromSchema(schema, true);

        Assertions.assertThat(generatedExample).hasToString("2020-01-02T00:00:00Z");
    }

    @ParameterizedTest
    @CsvSource({"binary,Y2F0c0lzQ29vbA==,catsIsCool", "byte,Y2F0c0lzQ29vbA==,catsIsCool", "binary,<binary string>,<binary string>"})
    void shouldFormatBinaryExample(String format, String value, String expected) throws Exception {
        Schema<byte[]> schema = new Schema<>();
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        schema.setFormat(format);
        schema.setType("string");
        schema.setExample(value.getBytes(StandardCharsets.UTF_8));

        Object formatted = generator.extractExampleFromSchema(schema, true);

        Assertions.assertThat(formatted).isInstanceOf(byte[].class);
        String decoded = new String((byte[]) formatted, StandardCharsets.UTF_8);
        Assertions.assertThat(decoded).isEqualTo(expected);
    }

    @Test
    void shouldReturnEmptyListWhenNullModel() throws Exception {
        OpenAPIModelGeneratorV2 generator = setupPayloadGenerator();
        List<String> example = generator.generate(null);
        Assertions.assertThat(example).isEmpty();
    }

    @Test
    void shouldGenerateOneOfWithParentPropertiesAndDiscriminator() throws Exception {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(Files.readString(Paths.get("src/test/resources/test-oneof-schema.yaml")), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        globalContext.getSchemaMap().putAll(schemas);
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat, new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);

        List<String> examples = generator.generate("AtomicValue");

        Assertions.assertThat(examples).hasSize(2);

        String firstExample = examples.getFirst();
        Assertions.assertThat(firstExample).contains("\"kind\"").contains("\"value\"").contains("\"bubulel\"");

        String kindValue = JsonUtils.getVariableFromJson(firstExample, "$.kind").toString();
        Assertions.assertThat(kindValue).isNotEmpty().isNotEqualTo("");

        String bubulel1 = JsonUtils.getVariableFromJson(firstExample, "$.bubulel").toString();
        Assertions.assertThat(bubulel1).isNotEmpty();

        String secondExample = examples.get(1);
        Assertions.assertThat(secondExample).contains("\"kind\"").contains("\"value\"").contains("\"bubulel\"");

        String kindValue2 = JsonUtils.getVariableFromJson(secondExample, "$.kind").toString();
        Assertions.assertThat(kindValue2).isNotEmpty().isNotEqualTo("");

        String bubulel2 = JsonUtils.getVariableFromJson(secondExample, "$.bubulel").toString();
        Assertions.assertThat(bubulel2).isNotEmpty();
    }

    @Test
    void shouldGenerateAllOfWithDiscriminatorParent() throws Exception {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(Files.readString(Paths.get("src/test/resources/test-allof-discriminator-schema.yaml")), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        globalContext.getSchemaMap().putAll(schemas);
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat, new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);

        List<String> decimalExamples = generator.generate("DecimalValue");

        Assertions.assertThat(decimalExamples).isNotEmpty();
        String decimalExample = decimalExamples.getFirst();

        Assertions.assertThat(decimalExample).contains("\"kind\"");
        Object kindValue = JsonUtils.getVariableFromJson(decimalExample, "$.kind");
        Assertions.assertThat(kindValue).isNotNull();
        Assertions.assertThat(kindValue.toString()).isNotEmpty().isNotEqualTo("")
                .isEqualTo("DECIMAL_VALUE");
    }

    @ParameterizedTest
    @CsvSource({
            "UPPER_SNAKE_CASE, DECIMAL_VALUE",
            "PascalCase, DecimalValue",
            "camelCase, decimalValue",
            "lower_snake_case, decimal_value",
            "kebab-case, decimal-value",
            "lowercase, decimalvalue"
    })
    void shouldGenerateAllOfWithDiscriminatorParentUsingConfiguredCasing(String discriminatorCasing, String expectedKind) throws Exception {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);

        OpenAPI openAPI = openAPIV3Parser
                .readContents(
                        Files.readString(Paths.get("src/test/resources/test-allof-discriminator-schema.yaml")),
                        null,
                        options)
                .getOpenAPI();

        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        globalContext.getSchemaMap().putAll(schemas);

        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(
                globalContext,
                validDataFormat,
                new ProcessingArguments.ExamplesFlags(true, true, true, true),
                3,
                true,
                2,
                discriminatorCasing
        );

        List<String> decimalExamples = generator.generate("DecimalValue");

        Assertions.assertThat(decimalExamples).isNotEmpty();

        Object kindValue = JsonUtils.getVariableFromJson(decimalExamples.getFirst(), "$.kind");

        Assertions.assertThat(kindValue).isNotNull().hasToString(expectedKind);
    }

    @Test
    void shouldGenerateOneOfDiscriminatorAsObjectNotString() throws Exception {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(Files.readString(Paths.get("src/test/resources/test-expression-node-schema.yaml")), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        globalContext.getSchemaMap().putAll(schemas);
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat, new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);

        List<String> examples = generator.generate("TheRequest");

        Assertions.assertThat(examples).isNotEmpty();

        List<String> examplesWithValue = examples.stream()
                .filter(ex -> ex.contains("\"value\""))
                .toList();

        Assertions.assertThat(examplesWithValue).isNotEmpty();

        // Verify that value is an object with kind property, not a simple string
        for (String example : examplesWithValue) {
            Object valueObj = JsonUtils.getVariableFromJson(example, "$.expression.value");

            // Value should be an object (Map), not a string
            Assertions.assertThat(valueObj).isInstanceOf(Map.class);

            // The object should have a kind field
            Object kindValue = JsonUtils.getVariableFromJson(example, "$.expression.value.kind");
            Assertions.assertThat(kindValue).isNotNull();
            Assertions.assertThat(kindValue.toString()).isIn("STRING_VALUE", "DECIMAL_VALUE", "INT_VALUE");
        }
    }

    @Test
    void shouldUseTheMappingOfTheOwningDiscriminatorWhenSeveralShareThePropertyName() throws Exception {
        OpenAPIModelGeneratorV2 generator = generatorFor("test-oneof-shared-discriminator-property.yaml");

        // Both unions are generated so that both discriminators are recorded: whichever one a
        // lookup of the first "type" discriminator returned, the other union would previously
        // miss its mapping and fall back to the casing of its schema name, e.g. BIG_CIRCLE_SHAPE.
        List<String> shapeTypes = generator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();
        List<String> vehicleTypes = generator.generate("Vehicle").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();

        Assertions.assertThat(shapeTypes).containsExactlyInAnyOrder("BIG_CIRCLE", "SMALL_SQUARE");
        Assertions.assertThat(vehicleTypes).containsExactlyInAnyOrder("SPORTS_CAR", "CARGO_TRUCK");
    }

    @Test
    void shouldUseEachUnionsPropertyNameWhenOneVariantBelongsToUnionsWithDifferentPropertyNames() throws Exception {
        OpenAPIModelGeneratorV2 generator = generatorFor("test-oneof-shared-variant-different-property-names.yaml");

        // BigCircleShape is a variant of both unions, and each union marks a different one of its
        // properties as the discriminator. Re-generate shapes on both the same and a fresh generator
        // instance (bypassing examplesCache) to ensure nothing is cached or left mutated based on order.
        List<String> shapeShapes = generator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.shape").toString())
                .toList();
        List<String> iconTypes = generator.generate("Icon").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();
        List<String> shapeShapesAfterIcon = generator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.shape").toString())
                .toList();
        OpenAPIModelGeneratorV2 freshGenerator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat,
                new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);
        List<String> shapeShapesFromFreshGenerator = freshGenerator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.shape").toString())
                .toList();

        Assertions.assertThat(shapeShapes).containsExactlyInAnyOrder("BIG_CIRCLE", "SMALL_SQUARE");
        Assertions.assertThat(iconTypes).containsExactlyInAnyOrder("BIG_CIRCLE", "WARNING");
        Assertions.assertThat(shapeShapesAfterIcon).containsExactlyInAnyOrder("BIG_CIRCLE", "SMALL_SQUARE");
        Assertions.assertThat(shapeShapesFromFreshGenerator).containsExactlyInAnyOrder("BIG_CIRCLE", "SMALL_SQUARE");
        Assertions.assertThat(globalContext.getSchemaFromReference("BigCircleShape").getDiscriminator()).isNull();
    }

    @Test
    void shouldUseOwningUnionMappingWhenSharedVariantUsesDifferentMappingKeysForSamePropertyName() throws Exception {
        OpenAPIModelGeneratorV2 generator = generatorFor("test-oneof-shared-variant-different-mapping-keys.yaml");

        List<String> shapeTypes = generator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();
        List<String> iconTypes = generator.generate("Icon").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();
        OpenAPIModelGeneratorV2 freshGenerator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat,
                new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);
        List<String> shapeTypesFromFreshGenerator = freshGenerator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();

        Assertions.assertThat(shapeTypes).containsExactlyInAnyOrder("BIG_CIRCLE", "SMALL_SQUARE");
        Assertions.assertThat(iconTypes).containsExactlyInAnyOrder("CIRCLE_ICON", "WARNING_ICON");
        Assertions.assertThat(shapeTypesFromFreshGenerator).containsExactlyInAnyOrder("BIG_CIRCLE", "SMALL_SQUARE");
    }

    @Test
    void shouldMatchMappingKeyContainedInSchemaNameAcrossMultipleDiscriminatorsWithSamePropertyName() throws Exception {
        OpenAPIModelGeneratorV2 generator = generatorFor("test-oneof-discriminator-key-substring-fallback.yaml");

        List<String> shapeTypes = generator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();
        List<String> vehicleTypes = generator.generate("Vehicle").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();

        Assertions.assertThat(shapeTypes).containsExactly("BigCircle");
        Assertions.assertThat(vehicleTypes).containsExactly("SportsCar");
    }

    @Test
    void shouldResolveOneOfDiscriminatorMappingWhenVariantsInheritDiscriminatorPropertyViaAllOf() throws Exception {
        OpenAPIModelGeneratorV2 generator = generatorFor("test-oneof-discriminator-allof-variants.yaml");

        List<String> shapeTypes = generator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();

        Assertions.assertThat(shapeTypes).containsExactlyInAnyOrder("BIG_CIRCLE", "SMALL_SQUARE");
        Assertions.assertThat(globalContext.getSchemaFromReference("BaseShape").getDiscriminator()).isNull();
    }

    @Test
    void shouldDetectCasingFromOwningUnionPartialMappingWhenUnionsShareDiscriminatorPropertyName() throws Exception {
        OpenAPIModelGeneratorV2 generator = generatorFor("test-oneof-partial-mapping-casing.yaml");

        List<String> vehicleTypes = generator.generate("Vehicle").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();
        List<String> shapeTypes = generator.generate("Shape").stream()
                .map(example -> JsonUtils.getVariableFromJson(example, "$.type").toString())
                .toList();

        Assertions.assertThat(vehicleTypes).containsExactly("SPORTS_CAR");
        Assertions.assertThat(shapeTypes).containsExactlyInAnyOrder("big-circle", "small-square-shape");
    }

    @Test
    void shouldGenerateFromExtensibleEnumAndNormalizeNumericPairs() {
        StringSchema opSchema = new StringSchema();
        opSchema.addExtension("x-extensible-enum", List.of("UNKNOWN", "SET"));
        io.swagger.v3.oas.models.media.IntegerSchema bedsSchema = new io.swagger.v3.oas.models.media.IntegerSchema();
        bedsSchema.setMinimum(java.math.BigDecimal.valueOf(10));
        bedsSchema.setMaximum(java.math.BigDecimal.valueOf(20));
        io.swagger.v3.oas.models.media.IntegerSchema availSchema = new io.swagger.v3.oas.models.media.IntegerSchema();
        availSchema.setMinimum(java.math.BigDecimal.valueOf(50));
        availSchema.setMaximum(java.math.BigDecimal.valueOf(60));
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("operation", opSchema);
        schema.addProperty("beds", bedsSchema);
        schema.addProperty("availableBeds", availSchema);
        globalContext.getSchemaMap().put("HospitalConfig", schema);

        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat,
                new ProcessingArguments.ExamplesFlags(false, false, false, false), 3, true, 2);
        String json = generator.generate("HospitalConfig").getFirst();

        Assertions.assertThat(JsonUtils.getVariableFromJson(json, "$.operation")).hasToString("SET");
        int beds = Integer.parseInt(JsonUtils.getVariableFromJson(json, "$.beds").toString());
        int availableBeds = Integer.parseInt(JsonUtils.getVariableFromJson(json, "$.availableBeds").toString());
        Assertions.assertThat(availableBeds).isLessThanOrEqualTo(beds);
    }

    @Test
    void shouldGenerateDeeplyNestedDeterministicObjectArraysWithoutExponentialBacktracking() {
        StringSchema fixedEnum = new StringSchema();
        fixedEnum.setEnum(List.of("ONLY_VALUE"));
        Schema<?> current = new ObjectSchema().addProperty("kind", fixedEnum);
        for (int depth = 1; depth <= 5; depth++) {
            ArraySchema arraySchema = new ArraySchema();
            arraySchema.setItems(current);
            current = new ObjectSchema()
                    .addProperty("kind", fixedEnum)
                    .addProperty("items" + depth, arraySchema);
        }
        globalContext.getSchemaMap().put("NestedRoot", current);

        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat,
                new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);
        long startNanos = System.nanoTime();
        List<String> generated = generator.generate("NestedRoot");
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        Assertions.assertThat(generated).isNotEmpty();
        Assertions.assertThat(generated.getFirst()).contains("ONLY_VALUE", "items5");
        Assertions.assertThat(elapsedMs).isLessThan(500L);
    }

    private OpenAPIModelGeneratorV2 generatorFor(String testResource) throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = new OpenAPIParser()
                .readContents(Files.readString(Paths.get("src/test/resources", testResource)), null, options)
                .getOpenAPI();
        globalContext.getSchemaMap().putAll(OpenApiUtils.getSchemas(openAPI, List.of("application/json")));
        return new OpenAPIModelGeneratorV2(globalContext, validDataFormat,
                new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);
    }

    private OpenAPIModelGeneratorV2 setupPayloadGenerator() throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        globalContext.getSchemaMap().putAll(schemas);
        return new OpenAPIModelGeneratorV2(globalContext, validDataFormat, new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);
    }
}
