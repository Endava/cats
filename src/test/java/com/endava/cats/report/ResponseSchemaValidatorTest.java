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
                  title: Billing Plans API
                  version: "1.0.0"
                paths:
                  /billing-plans:
                    get:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/PlanListResponse'
                components:
                  schemas:
                    PlanListResponse:
                      type: object
                      required:
                        - items
                      properties:
                        items:
                          type: array
                          items:
                            $ref: '#/components/schemas/PlanResponse'
                    PlanResponse:
                      oneOf:
                        - $ref: '#/components/schemas/FixedPlanResponse'
                        - $ref: '#/components/schemas/MeteredPlanResponse'
                      discriminator:
                        propertyName: planType
                        mapping:
                          FIXED: '#/components/schemas/FixedPlanResponse'
                          METERED: '#/components/schemas/MeteredPlanResponse'
                    PlanResponseBase:
                      type: object
                      required:
                        - planId
                        - planType
                        - name
                      properties:
                        planId:
                          type: string
                        planType:
                          type: string
                          enum: [FIXED, METERED]
                        name:
                          type: string
                    FixedPlanResponse:
                      allOf:
                        - $ref: '#/components/schemas/PlanResponseBase'
                    MeteredPlanResponse:
                      allOf:
                        - $ref: '#/components/schemas/PlanResponseBase'
                        - type: object
                          required:
                            - billingSchedule
                          properties:
                            billingSchedule:
                              type: array
                              items:
                                type: string
                """;
        OpenAPI openAPI = new io.swagger.v3.parser.OpenAPIV3Parser().readContents(openApiYaml, null, null).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, java.util.List.of("application/json"));
        Schema<?> responseSchema = openAPI.getPaths().get("/billing-plans").getGet()
                .getResponses().get("200").getContent().get("application/json").getSchema();

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .schemaMap(schemas)
                .responseSchemaDefinitions(Map.of("200", Map.of("application/json", responseSchema)))
                .build();

        CatsResponse validFixedPlanResponse = CatsResponse.builder()
                .responseCode(200)
                .responseContentType("application/json")
                .body("{\"items\":[{\"planId\":\"plan_1\",\"planType\":\"FIXED\",\"name\":\"Standard Monthly Plan\"}]}")
                .build();

        ResponseSchemaValidator validator = new ResponseSchemaValidator();
        ResponseSchemaValidator.ValidationResult result = validator.validate(validFixedPlanResponse, data);

        Assertions.assertThat(result.performed()).isTrue();
        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.errors()).isEmpty();
    }
}
