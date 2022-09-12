package com.endava.cats.model.generator;

import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@QuarkusTest
class PayloadGeneratorTest {

    @Inject
    CatsGlobalContext globalContext;

    @ParameterizedTest
    @CsvSource({"email,true", "emailAddress,true", "notEmail,true", "myEmailAddress,true", "randomField,false"})
    void shouldRecognizeEmail(String property, boolean expected) {
        StringSchema schema = new StringSchema();
        PayloadGenerator generator = new PayloadGenerator(globalContext, true, 3);
        boolean isEmail = generator.isEmailAddress(schema, property);
        Assertions.assertThat(isEmail).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"ip,true", "ipaddress,true", "hostIp,true", "something,false"})
    void shouldRecognizeIpAddressFromPropertyName(String property, boolean expected) {
        StringSchema schema = new StringSchema();
        PayloadGenerator generator = new PayloadGenerator(globalContext, true, 3);

        boolean isIp = generator.isIPV4(schema, property);
        Assertions.assertThat(isIp).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"ip,true", "ipv4,true", "hostIp,false", "something,false"})
    void shouldRecognizeIpAddressFromFormat(String format, boolean expected) {
        StringSchema schema = new StringSchema();
        schema.setFormat(format);
        PayloadGenerator generator = new PayloadGenerator(globalContext, true, 3);

        boolean isIp = generator.isIPV4(schema, "field");
        Assertions.assertThat(isIp).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"url,true", "uri,true", "addressurl,true", "addressuri,true", "not,false"})
    void shouldRecognizeUrlFromPropertyName(String property, boolean expected) {
        StringSchema schema = new StringSchema();
        PayloadGenerator generator = new PayloadGenerator(globalContext, true, 3);

        boolean isIp = generator.isURI(schema, property);
        Assertions.assertThat(isIp).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"url,true", "uri,true", "addressurl,false", "addressuri,false", "not,false"})
    void shouldRecognizeUrlFromPropertyFormat(String property, boolean expected) {
        StringSchema schema = new StringSchema();
        schema.setFormat(property);
        PayloadGenerator generator = new PayloadGenerator(globalContext, true, 3);

        boolean isIp = generator.isURI(schema, "other");
        Assertions.assertThat(isIp).isEqualTo(expected);
    }

    @Test
    void shouldRecognizeIpV6AddressFromPropertyName() {
        StringSchema schema = new StringSchema();
        PayloadGenerator generator = new PayloadGenerator(globalContext, true, 3);

        boolean isIp = generator.isIPV6(schema, "ipv6");
        Assertions.assertThat(isIp).isTrue();
    }

    @Test
    void shouldRecognizeIpV6AddressFromPropertyFormat() {
        StringSchema schema = new StringSchema();
        schema.setFormat("ipv6");
        PayloadGenerator generator = new PayloadGenerator(globalContext, true, 3);

        boolean isIp = generator.isIPV6(schema, "something");
        Assertions.assertThat(isIp).isTrue();
    }

    @Test
    void shouldReturnNotEmailWhenSchemaNull() {
        PayloadGenerator generator = new PayloadGenerator(globalContext, true, 3);

        Assertions.assertThat(generator.isEmailAddress(new Schema<>(), "field")).isFalse();
    }

    @Test
    void givenASimpleOpenAPIContract_whenGeneratingAPayload_thenTheExampleIsProperlyGenerated() throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();

        Map<String, String> example = generator.generate("Pet");
        String exampleJson = example.get("example");

        Assertions.assertThat(exampleJson)
                .contains("ALL_OF")
                .contains("breedONE_OF#/components/schemas/Husky")
                .contains("breedONE_OF#/components/schemas/Labrador");
    }

    @Test
    void shouldGenerateOneOfWhenOneOfInRoot() throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();

        Map<String, String> example = generator.generate("PetType");
        String exampleJson = example.get("example");

        Assertions.assertThat(exampleJson)
                .contains("PetTypeONE_OF#/components/schemas/Husky")
                .contains("PetTypeONE_OF#/components/schemas/Labrador");
    }

    @Test
    void shouldGenerateFromAllOfAndPreserveRootElement() throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();
        Map<String, String> example = generator.generate("MiniPet");
        String exampleJson = example.get("example");

        Assertions.assertThat(exampleJson).contains("color").contains("red").contains("green").contains("blue");
    }

    @ParameterizedTest
    @CsvSource({"MegaPet", "AdditionalPet", "ObjectPet"})
    void shouldGenerateAdditionalPropertiesWhenInlineDef(String modelName) throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();
        Map<String, String> example = generator.generate(modelName);
        String exampleJson = example.get("example");

        Assertions.assertThat(exampleJson).contains("metadata");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "metadata#key")).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyGenerateWhenCyclicReference() throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();
        Map<String, String> example = generator.generate("CyclicPet");
        String exampleJson = example.get("example");

        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#parent#parent#code")).isNotEqualTo("NOT_SET");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#parent#parent#parent#parent#parent#parent#code")).isEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyFormatDateExamples() throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();
        Map<String, String> example = generator.generate("MegaPet");
        String exampleJson = example.get("example");

        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#dateOfBirth")).isEqualTo("2000-12-12");
        Assertions.assertThat(JsonUtils.getVariableFromJson(exampleJson, "$#timeOfVaccination")).isEqualTo("2012-01-24T15:54:14.876Z");
    }

    private PayloadGenerator setupPayloadGenerator() throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/petstore.yml"))), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        globalContext.getSchemaMap().putAll(schemas);
        return new PayloadGenerator(globalContext, true, 3);
    }
}
