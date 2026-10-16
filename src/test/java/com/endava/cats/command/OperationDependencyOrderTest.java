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

    private static Operation post(Schema<?> schema) {
        return new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(schema))));
    }
}
