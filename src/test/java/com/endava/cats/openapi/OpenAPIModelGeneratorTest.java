package com.endava.cats.openapi;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

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
class OpenAPIModelGeneratorTest {

    @Inject
    CatsGlobalContext globalContext;
    @Inject
    ValidDataFormat validDataFormat;

    @Test
    void givenASimpleOpenAPIContract_whenGeneratingAPayload_thenTheExampleIsProperlyGenerated() throws Exception {
        OpenAPIModelGenerator generator = setupPayloadGenerator();

        Map<String, String> example = generator.generate("Pet");
        String exampleJson = example.get("example");

        Assertions.assertThat(exampleJson)
                .contains("ALL_OF")
                .contains("breedONE_OF#/components/schemas/Husky")
                .contains("breedONE_OF#/components/schemas/Labrador");
    }

    @Test
    void shouldGenerateOneOfWhenOneOfInRoot() throws Exception {
        OpenAPIModelGenerator generator = setupPayloadGenerator();

        Map<String, String> example = generator.generate("PetType");
        String exampleJson = example.get("example");

        Assertions.assertThat(exampleJson)
                .contains("ONE_OF#/components/schemas/Husky")
                .contains("ONE_OF#/components/schemas/Labrador");
    }

    @Test
    void shouldGenerateFromAllOfAndPreserveRootElement() throws Exception {
        OpenAPIModelGenerator generator = setupPayloadGenerator();
        Map<String, String> example = generator.generate("MiniPet");
        String exampleJson = example.get("example");

        Assertions.assertThat(exampleJson).contains("color").contains("red").contains("green").contains("blue");
    }

    @ParameterizedTest
    @CsvSource({"MegaPet", "AdditionalPet", "ObjectPet"})
    void shouldGenerateAdditionalPropertiesWhenInlineDef(String modelName) throws Exception {
        OpenAPIModelGenerator generator = setupPayloadGenerator();
        Map<String, String> example = generator.generate(modelName);
        String exampleJson = example.get("example");

        Assertions.assertThat(exampleJson).contains("metadata");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "metadata#key")).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyGenerateWhenCyclicReference() throws Exception {
        OpenAPIModelGenerator generator = setupPayloadGenerator();
        Map<String, String> example = generator.generate("CyclicPet");
        String exampleJson = example.get("example");

        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#parent#parent#code")).isNotEqualTo("NOT_SET");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#parent#parent#parent#parent#parent#parent#code")).isEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyFormatDateExamples() throws Exception {
        OpenAPIModelGenerator generator = setupPayloadGenerator();
        Map<String, String> example = generator.generate("MegaPet");
        String exampleJson = example.get("example");

        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#dateOfBirth")).asString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#timeOfVaccination")).asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{1,16}Z");
    }

    @ParameterizedTest
    @CsvSource(value = {"null,1,1", "0,1,1", "null,null,2", "1,null,2", "2,3,2"}, nullValues = "null")
    void shouldComputeProperArrayLength(Integer min, Integer max, int expected) throws Exception {
        OpenAPIModelGenerator generator = setupPayloadGenerator();

        Schema<?> schema = new Schema<>();
        schema.setMinItems(min);
        schema.setMaxItems(max);
        int result = generator.getArrayLength(schema);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldFormatDateExample() throws Exception {
        Schema<Date> schema = new Schema<>();
        OpenAPIModelGenerator generator = setupPayloadGenerator();
        schema.setExample(Date.from(Instant.parse("2020-01-02T00:00:00Z")));
        schema.setType("string");
        schema.setFormat("date");

        Object formatted = generator.extractExampleFromSchema(schema, true);

        Assertions.assertThat(formatted).hasToString("2020-01-02");
    }

    @Test
    void shouldRemoveNewLine() throws Exception {
        Schema<String> schema = new Schema<>();
        OpenAPIModelGenerator generator = setupPayloadGenerator();
        schema.setType("string");
        schema.setExample("This is a string\nwith a new line");

        Object formatted = generator.extractExampleFromSchema(schema, true);

        Assertions.assertThat(formatted).hasToString("This is a stringwith a new line");
    }

    @Test
    void shouldFormatDateTimeExample() throws Exception {
        Schema<Date> schema = new Schema<>();
        OpenAPIModelGenerator generator = setupPayloadGenerator();
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
        OpenAPIModelGenerator generator = setupPayloadGenerator();
        schema.setFormat(format);
        schema.setType("string");
        schema.setExample(value.getBytes(StandardCharsets.UTF_8));

        Object formatted = generator.extractExampleFromSchema(schema, true);

        Assertions.assertThat(formatted).isInstanceOf(byte[].class);
        String decoded = new String((byte[]) formatted, StandardCharsets.UTF_8);
        Assertions.assertThat(decoded).isEqualTo(expected);
    }

    @Test
    void shouldSerializeWithTheDepthAwareSerializer() throws Exception {
        OpenAPIModelGenerator generator = setupPayloadGenerator();
        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setProperties(Map.of("field1", new Schema<>(), "field2", new Schema<>()));
        schema.setExample(Map.of("field1", Map.of("field2", Map.of("field3", "value"))));
        ObjectMapper mockMapper = Mockito.mock(ObjectMapper.class);
        Mockito.when(mockMapper.writeValueAsString(Mockito.any())).thenThrow(new JsonParseException("simulating issue"));
        ReflectionTestUtils.setField(generator, "simpleObjectMapper", mockMapper);
        String result = generator.tryToSerializeExample(schema);

        Assertions.assertThat(result).isNull();
    }

    private OpenAPIModelGenerator setupPayloadGenerator() throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        globalContext.getSchemaMap().putAll(schemas);
        return new OpenAPIModelGenerator(globalContext, validDataFormat, new ProcessingArguments.ExamplesFlags(true, true, true, true), 3, true, 2);
    }
}
