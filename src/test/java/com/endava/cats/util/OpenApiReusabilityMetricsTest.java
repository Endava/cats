package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@QuarkusTest
class OpenApiReusabilityMetricsTest {

    @Test
    void shouldComputeAllMetricsForRichOpenApiSpec() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/petstore.yml");
        OpenApiReusabilityMetrics.OpenApiMetricsResult metrics = OpenApiReusabilityMetrics.computeAllMetrics(openAPI);

        Assertions.assertThat(metrics).isNotNull();
        Assertions.assertThat(metrics.componentInvocationCoverage()).isBetween(0.0, 1.0);
        Assertions.assertThat(metrics.deadComponentRatio()).isBetween(0.0, 1.0);
        Assertions.assertThat(metrics.inlineDefinitionRatio()).isBetween(0.0, 1.0);
        Assertions.assertThat(metrics.averageRefsPerOperation()).isGreaterThanOrEqualTo(0.0);
        Assertions.assertThat(metrics.uniqueTargetReuseRatio()).isBetween(0.0, 1.0);
        Assertions.assertThat(metrics.errorResponseNameDiversity()).isBetween(0.0, 1.0);
        Assertions.assertThat(metrics.tagCohesionIndex()).isGreaterThanOrEqualTo(0.0);
        Assertions.assertThat(metrics.optionalToRequiredFieldRatio()).isBetween(0.0, 1.0);
        Assertions.assertThat(metrics.toString()).contains("OpenAPI Metrics:", "Component Invocation Coverage:");
    }

    @Test
    void shouldHandleEmptyOpenApiAndErrorResponseVariants() {
        OpenAPI emptyApi = new OpenAPI();
        OpenApiReusabilityMetrics.OpenApiMetricsResult emptyMetrics = OpenApiReusabilityMetrics.computeAllMetrics(emptyApi);
        Assertions.assertThat(emptyMetrics.componentInvocationCoverage()).isZero();
        Assertions.assertThat(emptyMetrics.deadComponentRatio()).isZero();
        Assertions.assertThat(emptyMetrics.inlineDefinitionRatio()).isZero();
        Assertions.assertThat(emptyMetrics.averageRefsPerOperation()).isZero();
        Assertions.assertThat(emptyMetrics.uniqueTargetReuseRatio()).isZero();
        Assertions.assertThat(emptyMetrics.errorResponseNameDiversity()).isZero();
        Assertions.assertThat(emptyMetrics.tagCohesionIndex()).isZero();
        Assertions.assertThat(emptyMetrics.optionalToRequiredFieldRatio()).isZero();

        OpenAPI customApi = new OpenAPI();
        Components components = new Components()
                .schemas(Map.of("ErrModel", new ObjectSchema().addProperty("code", new StringSchema()).required(List.of("code"))))
                .responses(Map.of("NotFoundResp", new ApiResponse().description("Not found")))
                .parameters(Map.of("LimitParam", new Parameter().name("limit").in("query")))
                .requestBodies(Map.of("BodyDef", new RequestBody()))
                .headers(Map.of("X-Rate", new Header()));
        customApi.setComponents(components);

        Operation getOp = new Operation()
                .tags(List.of("items"))
                .responses(new ApiResponses()
                        .addApiResponse("404", new ApiResponse().$ref("#/components/responses/NotFoundResp"))
                        .addApiResponse("400", new ApiResponse().content(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrModel")))))
                        .addApiResponse("500", new ApiResponse().description("Inline error")));
        customApi.setPaths(new Paths().addPathItem("/items", new PathItem().get(getOp)));

        OpenApiReusabilityMetrics.OpenApiMetricsResult customMetrics = OpenApiReusabilityMetrics.computeAllMetrics(customApi);
        Assertions.assertThat(customMetrics.errorResponseNameDiversity()).isGreaterThan(0.0);
        Assertions.assertThat(customMetrics.tagCohesionIndex()).isEqualTo(1.0);
        Assertions.assertThat(customMetrics.deadComponentRatio()).isGreaterThan(0.0);
    }
}
