package com.endava.cats.openapi;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
