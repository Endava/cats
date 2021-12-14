package com.endava.cats.generator.simple;

import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.util.OpenApiUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

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

    @Test
    void givenASimpleOpenAPIContract_whenGeneratingAPayload_thenTheExampleIsProperlyGenerated() throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();

        List<Map<String, String>> example = generator.generate(null, "Pet");
        String exampleJson = example.get(0).get("example");

        Assertions.assertThat(exampleJson)
                .contains("ALL_OF")
                .contains("breedONE_OF#/components/schemas/Husky")
                .contains("breedONE_OF#/components/schemas/Labrador");
    }

    @Test
    void shouldGenerateOneOfWhenOneOfInRoot() throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();

        List<Map<String, String>> example = generator.generate(null, "PetType");
        String exampleJson = example.get(0).get("example");

        Assertions.assertThat(exampleJson)
                .contains("PetTypeONE_OF#/components/schemas/Husky")
                .contains("PetTypeONE_OF#/components/schemas/Labrador");
    }

    @Test
    void shouldGenerateFromAllOfAndPreserveRootElement() throws Exception {
        PayloadGenerator generator = setupPayloadGenerator();
        List<Map<String, String>> example = generator.generate(null, "MiniPet");
        String exampleJson = example.get(0).get("example");

        Assertions.assertThat(exampleJson).contains("color").contains("red").contains("green").contains("blue");
    }

    private PayloadGenerator setupPayloadGenerator() throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/petstore.yml"))), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, "application/json");
        globalContext.getSchemaMap().putAll(schemas);
        return new PayloadGenerator(globalContext, true);
    }
}
