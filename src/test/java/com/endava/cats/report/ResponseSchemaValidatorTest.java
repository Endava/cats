package com.endava.cats.report;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.OpenApiUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
class ResponseSchemaValidatorTest {

    @Test
    void shouldValidateOneOfResponseSchemaWithExplicitDiscriminatorMapping() {
        String openApiYaml = """
                openapi: "3.0.1"
                info:
                  title: Risk Rules API
                  version: "1.0.0"
                paths:
                  /risk-rules:
                    get:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/RuleListResponse'
                components:
                  schemas:
                    RuleListResponse:
                      type: object
                      required:
                        - items
                      properties:
                        items:
                          type: array
                          items:
                            $ref: '#/components/schemas/RuleResponse'
                    RuleResponse:
                      oneOf:
                        - $ref: '#/components/schemas/StaticRuleResponse'
                        - $ref: '#/components/schemas/VelocityRuleResponse'
                      discriminator:
                        propertyName: ruleType
                        mapping:
                          STATIC: '#/components/schemas/StaticRuleResponse'
                          VELOCITY: '#/components/schemas/VelocityRuleResponse'
                    RuleResponseBase:
                      type: object
                      required:
                        - ruleId
                        - ruleType
                        - name
                      properties:
                        ruleId:
                          type: string
                        ruleType:
                          type: string
                          enum: [STATIC, VELOCITY]
                        name:
                          type: string
                    StaticRuleResponse:
                      allOf:
                        - $ref: '#/components/schemas/RuleResponseBase'
                    VelocityRuleResponse:
                      allOf:
                        - $ref: '#/components/schemas/RuleResponseBase'
                        - type: object
                          required:
                            - velocityDefinitions
                          properties:
                            velocityDefinitions:
                              type: array
                              items:
                                type: string
                """;
        OpenAPI openAPI = new io.swagger.v3.parser.OpenAPIV3Parser().readContents(openApiYaml, null, null).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, java.util.List.of("application/json"));
        Schema<?> responseSchema = openAPI.getPaths().get("/risk-rules").getGet()
                .getResponses().get("200").getContent().get("application/json").getSchema();

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .schemaMap(schemas)
                .responseSchemaDefinitions(Map.of("200", Map.of("application/json", responseSchema)))
                .build();

        CatsResponse validStaticRuleResponse = CatsResponse.builder()
                .responseCode(200)
                .responseContentType("application/json")
                .body("{\"items\":[{\"ruleId\":\"rule_1\",\"ruleType\":\"STATIC\",\"name\":\"High-Risk Country Rule\"}]}")
                .build();

        ResponseSchemaValidator validator = new ResponseSchemaValidator();
        ResponseSchemaValidator.ValidationResult result = validator.validate(validStaticRuleResponse, data);

        Assertions.assertThat(result.performed()).isTrue();
        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.errors()).isEmpty();
    }
}
