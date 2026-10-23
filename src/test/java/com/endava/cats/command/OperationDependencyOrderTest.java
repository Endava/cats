package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@QuarkusTest
class OperationDependencyOrderTest {
    @Test
    void schedulesExplicitProducerBeforeConsumerWithoutChangingUnrelatedOrder() {
        OpenAPI api = new OpenAPI().components(new Components().addSchemas("Order",
                        new ObjectSchema().addProperty("petId", new StringSchema())))
                .paths(new Paths()
                        .addPathItem("/orders", new PathItem().post(post(new Schema<>().$ref("#/components/schemas/Order"))))
                        .addPathItem("/pets", new PathItem().post(new Operation()))
                        .addPathItem("/unrelated", new PathItem().get(new Operation())));
        List<Map.Entry<String, PathItem>> alphabetical = api.getPaths().entrySet().stream().toList();

        Assertions.assertThat(OperationDependencyOrder.order(api, alphabetical)).extracting(Map.Entry::getKey)
                .containsExactly("/pets", "/orders", "/unrelated");
    }

    @Test
    void doesNotInventDependenciesForUnrelatedOrUnresolvedSchemas() {
        OpenAPI api = new OpenAPI().paths(new Paths()
                .addPathItem("/orders", new PathItem().post(post(new Schema<>().$ref("#/components/schemas/Unknown"))))
                .addPathItem("/pets", new PathItem().post(new Operation())));
        List<Map.Entry<String, PathItem>> alphabetical = api.getPaths().entrySet().stream().toList();

        Assertions.assertThat(OperationDependencyOrder.order(api, alphabetical)).extracting(Map.Entry::getKey)
                .containsExactly("/orders", "/pets");
    }

    @Test
    void schedulesProducersReferencedViaAllOfNestedObjectsQueryParametersAndIrregularOrEsPlurals() {
        Schema<?> baseShipment = new ObjectSchema().addProperty("boxUuid", new StringSchema());
        Schema<?> shipmentRequest = new ComposedSchema()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/BaseShipment"))
                .addAllOfItem(new ObjectSchema().addProperty("recipient",
                        new ObjectSchema().addProperty("id", new StringSchema())));
        OpenAPI api = new OpenAPI()
                .components(new Components()
                        .addSchemas("BaseShipment", baseShipment)
                        .addSchemas("ShipmentRequest", shipmentRequest))
                .paths(new Paths()
                        .addPathItem("/appointments", new PathItem().get(new Operation()
                                .addParametersItem(new QueryParameter().name("personId"))))
                        .addPathItem("/shipments", new PathItem().post(post(new Schema<>().$ref("#/components/schemas/ShipmentRequest"))))
                        .addPathItem("/boxes", new PathItem().post(new Operation()))
                        .addPathItem("/people", new PathItem().post(new Operation()))
                        .addPathItem("/recipients", new PathItem().post(new Operation())));
        List<Map.Entry<String, PathItem>> alphabetical = api.getPaths().entrySet().stream().toList();

        Assertions.assertThat(OperationDependencyOrder.order(api, alphabetical)).extracting(Map.Entry::getKey)
                .containsExactly("/people", "/appointments", "/boxes", "/recipients", "/shipments");
    }

    @Test
    void schedulesTrailingSlashAndGenericIdPathDependencies() {
        OpenAPI api = new OpenAPI().paths(new Paths()
                .addPathItem("/v1/hospitais/{hospital_id}", new PathItem().get(new Operation()))
                .addPathItem("/v1/hospitais/", new PathItem().post(new Operation()))
                .addPathItem("/api/flights/{id}", new PathItem().get(new Operation()))
                .addPathItem("/api/flights", new PathItem().post(new Operation())));
        List<Map.Entry<String, PathItem>> items = api.getPaths().entrySet().stream().toList();

        Assertions.assertThat(OperationDependencyOrder.order(api, items)).extracting(Map.Entry::getKey)
                .containsExactly("/v1/hospitais/", "/v1/hospitais/{hospital_id}", "/api/flights", "/api/flights/{id}");
    }

    @Test
    void schedulesSupersetSiblingPostBeforeSubsetAndResponseTokenProducersBeforeConsumers() {
        Schema<?> registerSchema = new ObjectSchema()
                .addProperty("email", new StringSchema())
                .addProperty("password", new StringSchema())
                .addProperty("firstName", new StringSchema());
        Schema<?> loginSchema = new ObjectSchema()
                .addProperty("email", new StringSchema())
                .addProperty("password", new StringSchema());
        Schema<?> loginResponseSchema = new ObjectSchema()
                .addProperty("accessToken", new StringSchema())
                .addProperty("refreshToken", new StringSchema());
        Schema<?> logoutSchema = new ObjectSchema()
                .addProperty("accessToken", new StringSchema())
                .addProperty("refreshToken", new StringSchema());
        Operation loginOp = post(new Schema<>().$ref("#/components/schemas/LoginRequest"))
                .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                        .addApiResponse("200", new io.swagger.v3.oas.models.responses.ApiResponse()
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/LoginResponse"))))));
        OpenAPI api = new OpenAPI()
                .components(new Components()
                        .addSchemas("RegisterRequest", registerSchema)
                        .addSchemas("LoginRequest", loginSchema)
                        .addSchemas("LoginResponse", loginResponseSchema)
                        .addSchemas("LogoutRequest", logoutSchema))
                .paths(new Paths()
                        .addPathItem("/api/auth/login", new PathItem().post(loginOp))
                        .addPathItem("/api/auth/logout", new PathItem().post(post(new Schema<>().$ref("#/components/schemas/LogoutRequest"))))
                        .addPathItem("/api/auth/register", new PathItem().post(post(new Schema<>().$ref("#/components/schemas/RegisterRequest")))));
        List<Map.Entry<String, PathItem>> items = api.getPaths().entrySet().stream().toList();

        Assertions.assertThat(OperationDependencyOrder.order(api, items)).extracting(Map.Entry::getKey)
                .containsExactly("/api/auth/register", "/api/auth/login", "/api/auth/logout");
    }

    @Test
    void schedulesIeSingularProducerBeforeConsumer() {
        Schema<?> booking = new ObjectSchema().addProperty("propertyId", new StringSchema());
        OpenAPI api = new OpenAPI().paths(new Paths()
                .addPathItem("/bookings", new PathItem().post(post(booking)))
                .addPathItem("/properties", new PathItem().post(new Operation())));
        List<Map.Entry<String, PathItem>> alphabetical = api.getPaths().entrySet().stream().toList();

        Assertions.assertThat(OperationDependencyOrder.order(api, alphabetical)).extracting(Map.Entry::getKey)
                .containsExactly("/properties", "/bookings");
    }

    @Test
    void schedulesTemplatedPostProducersBeforeDeeperAndCamelCaseConsumersAndHandlesRootSlash() {
        Schema<?> requiresConstraint = new ObjectSchema()
                .addProperty("sourceFeature", new StringSchema())
                .addProperty("requiredFeature", new StringSchema());
        OpenAPI api = new OpenAPI().paths(new Paths()
                .addPathItem("/", new PathItem().post(new Operation()))
                .addPathItem("/products/{productName}/constraints/requires", new PathItem().post(post(requiresConstraint)))
                .addPathItem("/products/{productName}/features/{featureName}", new PathItem().post(new Operation()))
                .addPathItem("/products/{productName}", new PathItem().post(new Operation())));
        List<Map.Entry<String, PathItem>> alphabetical = api.getPaths().entrySet().stream().toList();

        Assertions.assertThat(OperationDependencyOrder.order(api, alphabetical)).extracting(Map.Entry::getKey)
                .containsExactly(
                        "/",
                        "/products/{productName}",
                        "/products/{productName}/features/{featureName}",
                        "/products/{productName}/constraints/requires");
    }

    private static Operation post(Schema<?> schema) {
        return new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(schema))));
    }
}
