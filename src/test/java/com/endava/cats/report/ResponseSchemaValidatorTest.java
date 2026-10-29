package com.endava.cats.report;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.OpenApiUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
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
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(openApiYaml, null, null).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
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
        Assertions.assertThat(result.details()).isEmpty();

        // Second call hits compiledByIdentity cache
        Assertions.assertThat(validator.validate(validFixedPlanResponse, data).isValid()).isTrue();

        // Different Schema instance with same $ref hits compiledByKey cache
        Schema<?> sameRefSchema = new Schema<>().$ref("#/components/schemas/PlanListResponse");
        FuzzingData sameRefData = FuzzingData.builder()
                .openApi(openAPI)
                .schemaMap(schemas)
                .responseSchemaDefinitions(Map.of("200", Map.of("application/json", sameRefSchema)))
                .build();
        Assertions.assertThat(validator.validate(validFixedPlanResponse, sameRefData).isValid()).isTrue();

        // Invalid response body with > 5 errors truncates details()
        ResponseSchemaValidator.ValidationResult manyErrors = ResponseSchemaValidator.ValidationResult.invalid(
                List.of("e1", "e2", "e3", "e4", "e5", "e6", "e7"));
        Assertions.assertThat(manyErrors.details()).contains("... and 2 more validation errors");

        // Null data / missing schema / invalid JSON body
        Assertions.assertThat(validator.validate(validFixedPlanResponse, null).performed()).isFalse();
        Assertions.assertThat(validator.validate(validFixedPlanResponse, FuzzingData.builder().build()).performed()).isFalse();
        CatsResponse unmappedCodeResponse = CatsResponse.builder().responseCode(204).responseContentType("application/json").body("").build();
        Assertions.assertThat(validator.validate(unmappedCodeResponse, data).performed()).isFalse();
        CatsResponse malformedBodyResponse = CatsResponse.builder().responseCode(200).responseContentType("application/json").body("{not-json").build();
        Assertions.assertThat(validator.validate(malformedBodyResponse, data).isValid()).isFalse();
    }

    @Test
    void shouldValidateSpringDocOpenApi31OneOfWithBaseSchemaAndImplicitDiscriminatorMapping() {
        String openApiJson = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "Pet Shelter API", "version": "1.0.0" },
                  "paths": {
                    "/pets/{petId}": {
                      "get": {
                        "responses": {
                          "200": {
                            "description": "OK",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/PetAdoptionResponse" }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "PetAdoptionResponse": {
                        "type": "object",
                        "required": ["adoptionId", "pet"],
                        "properties": {
                          "adoptionId": { "type": "string" },
                          "pet": {
                            "oneOf": [
                              { "$ref": "#/components/schemas/Pet" },
                              { "$ref": "#/components/schemas/Cat" },
                              { "$ref": "#/components/schemas/Dog" },
                              { "$ref": "#/components/schemas/Puppy" },
                              { "$ref": "#/components/schemas/Lizard" }
                            ]
                          }
                        }
                      },
                      "Pet": {
                        "discriminator": { "propertyName": "petType" },
                        "properties": { "petType": { "type": "string" } },
                        "required": ["petType"]
                      },
                      "Cat": {
                        "allOf": [
                          { "$ref": "#/components/schemas/Pet" },
                          {
                            "type": "object",
                            "properties": {
                              "huntingSkill": { "type": "string" }
                            }
                          }
                        ]
                      },
                      "Dog": {
                        "allOf": [
                          { "$ref": "#/components/schemas/Pet" },
                          {
                            "type": "object",
                            "properties": {
                              "breed": { "type": "string" }
                            }
                          }
                        ],
                        "required": ["breed"]
                      },
                      "Puppy": {
                        "allOf": [
                          { "$ref": "#/components/schemas/Dog" },
                          {
                            "type": "object",
                            "properties": {
                              "monthsOld": { "type": "integer" }
                            }
                          }
                        ]
                      },
                      "Lizard": {
                        "allOf": [
                          { "$ref": "#/components/schemas/Pet" },
                          {
                            "type": "object",
                            "properties": {
                              "lovesRocks": { "type": "boolean" }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(openApiJson, null, null).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application/json"));
        Schema<?> responseSchema = openAPI.getPaths().get("/pets/{petId}").getGet()
                .getResponses().get("200").getContent().get("application/json").getSchema();

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .schemaMap(schemas)
                .responseSchemaDefinitions(Map.of("200", Map.of("application/json", responseSchema)))
                .build();

        CatsResponse validLizardResponse = CatsResponse.builder()
                .responseCode(200)
                .responseContentType("application/json")
                .body("{\"adoptionId\":\"adopt_1\",\"pet\":{\"petType\":\"LIZARD\",\"lovesRocks\":true}}")
                .build();

        ResponseSchemaValidator validator = new ResponseSchemaValidator();
        ResponseSchemaValidator.ValidationResult result = validator.validate(validLizardResponse, data);

        Assertions.assertThat(result.performed()).isTrue();
        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.errors()).isEmpty();

        CatsResponse validPuppyResponse = CatsResponse.builder()
                .responseCode(200)
                .responseContentType("application/json")
                .body("{\"adoptionId\":\"adopt_2\",\"pet\":{\"petType\":\"PUPPY\",\"breed\":\"Beagle\",\"monthsOld\":3}}")
                .build();
        ResponseSchemaValidator.ValidationResult puppyResult = validator.validate(validPuppyResponse, data);
        Assertions.assertThat(puppyResult.isValid()).isTrue();
    }
}
