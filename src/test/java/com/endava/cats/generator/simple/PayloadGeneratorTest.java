package com.endava.cats.generator.simple;

import com.endava.cats.CatsMain;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class PayloadGeneratorTest {


    @Test
    public void givenASimpleOpenAPIContract_whenGeneratingAPayload_thenTheExampleIsProperlyGenerated() throws Exception {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/petstore.yml"))), null, options).getOpenAPI();
        Map<String, Schema<?>> schemas = CatsMain.getSchemas(openAPI);

        PayloadGenerator generator = new PayloadGenerator(schemas);

        List<Map<String, String>> example = generator.generate(null, "Pet");
        String exampleJson = example.get(0).get("example");

        Assertions.assertThat(exampleJson)
                .contains("ALL_OF")
                .contains("breedONE_OF#/components/schemas/Husky")
                .contains("breedONE_OF#/components/schemas/Labrador");
    }
}
