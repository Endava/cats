package com.endava.cats.model.generator;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.openapi.OpenApiUtils;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    @CsvSource(value = {"null,1,1", "null,null,2", "1,null,1", "2,3,3"}, nullValues = "null")
    void shouldComputeProperArrayLength(Integer min, Integer max, int expected) {
        Schema<?> schema = new Schema<>();
        schema.setMinItems(min);
        schema.setMaxItems(max);
        int result = OpenAPIModelGenerator.getArrayLength(schema);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    private OpenAPIModelGenerator setupPayloadGenerator() throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        globalContext.getSchemaMap().putAll(schemas);
        return new OpenAPIModelGenerator(globalContext, validDataFormat, true, 3, true);
    }
}
