package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

@QuarkusTest
class OnlyRequiredFieldsFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private OnlyRequiredFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        fuzzer = new OnlyRequiredFieldsFuzzer(simpleExecutor);
    }

    @Test
    void shouldSendOnlyRequiredFieldsAndExpectSuccess() {
        ObjectSchema detailsSchema = objectSchema(
                Map.of("name", new StringSchema(), "nickname", new StringSchema()),
                List.of("name"));
        ObjectSchema optionalContainerSchema = objectSchema(
                Map.of("requiredWhenContainerIsPresent", new StringSchema()),
                List.of("requiredWhenContainerIsPresent"));
        ObjectSchema itemSchema = objectSchema(
                Map.of("code", new StringSchema(), "label", new StringSchema()),
                List.of("code"));
        ArraySchema itemsSchema = new ArraySchema().items(itemSchema);
        ObjectSchema rootSchema = objectSchema(Map.of(
                "id", new StringSchema(),
                "description", new StringSchema(),
                "details", detailsSchema,
                "optionalContainer", optionalContainerSchema,
                "items", itemsSchema), List.of("id", "details", "items"));
        FuzzingData data = fuzzingData("""
                {
                  "id": "123",
                  "description": "optional",
                  "details": {"name": "required", "nickname": "optional"},
                  "optionalContainer": {"requiredWhenContainerIsPresent": "value"},
                  "items": [
                    {"code": "A", "label": "optional"},
                    {"code": "B", "label": "optional"}
                  ]
                }
                """, rootSchema, Map.of(), List.of("id", "details", "details#name", "items", "items#code"));

        fuzzer.fuzz(data);

        SimpleExecutorContext context = capturedContext();
        Assertions.assertThat(JsonUtils.equalAsJson(context.getPayload(), """
                {
                  "id": "123",
                  "details": {"name": "required"},
                  "items": [{"code": "A"}, {"code": "B"}]
                }
                """)).isTrue();
        Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(context.getScenario()).contains("only required fields");
        Assertions.assertThat(context.getFuzzingData()).isSameAs(data);
        Assertions.assertThat(context.getMutationTargets()).containsExactly(RequestTarget.requestBody());
    }

    @Test
    void shouldReportOptionalQueryParametersAsTheTargetForMethodsWithoutBodies() {
        ObjectSchema schema = objectSchema(
                Map.of("required", new StringSchema(), "optional", new StringSchema()),
                List.of("required"));
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.GET)
                .path("/path")
                .payload("{\"required\":\"yes\",\"optional\":\"no\"}")
                .reqSchema(schema)
                .schemaMap(Map.of())
                .allRequiredFields(List.of("required"))
                .requestPropertyTypes(Map.of())
                .requestContentTypes(List.of("application/json"))
                .build();

        fuzzer.fuzz(data);

        Assertions.assertThat(capturedContext().getMutationTargets())
                .containsExactly(RequestTarget.query("Optional parameters"));
    }

    @Test
    void shouldApplyItemSchemaToEveryElementOfARootArray() {
        ObjectSchema itemSchema = objectSchema(
                Map.of("id", new StringSchema(), "optional", new StringSchema()),
                List.of("id"));
        FuzzingData data = fuzzingData("""
                [{"id":"one","optional":"x"},{"id":"two","optional":"y"}]
                """, itemSchema, Map.of(), List.of("id"));

        fuzzer.fuzz(data);

        Assertions.assertThat(JsonUtils.equalAsJson(capturedContext().getPayload(),
                "[{\"id\":\"one\"},{\"id\":\"two\"}]")).isTrue();
    }

    @Test
    void shouldResolveReferencesAndMergeAllOfSchemas() {
        ObjectSchema baseSchema = objectSchema(
                Map.of("baseId", new StringSchema(), "baseOptional", new StringSchema()),
                List.of("baseId"));
        Schema<?> baseReference = new Schema<>().$ref("#/components/schemas/Base");
        ObjectSchema extensionSchema = objectSchema(
                Map.of("name", new StringSchema(), "extensionOptional", new StringSchema()),
                List.of("name"));
        ComposedSchema composedSchema = new ComposedSchema();
        composedSchema.setAllOf(List.of(baseReference, extensionSchema));
        FuzzingData data = fuzzingData("""
                {"baseId":"1","baseOptional":"x","name":"cats","extensionOptional":"y"}
                """, composedSchema, Map.of("Base", baseSchema), List.of("baseId", "name"));

        fuzzer.fuzz(data);

        Assertions.assertThat(JsonUtils.equalAsJson(capturedContext().getPayload(),
                "{\"baseId\":\"1\",\"name\":\"cats\"}")).isTrue();
    }

    @Test
    void shouldUseRequiredFieldsAssociatedWithThePayloadVariant() {
        ObjectSchema catSchema = objectSchema(
                Map.of("catName", new StringSchema(), "catOptional", new StringSchema()),
                List.of("catName"));
        ObjectSchema dogSchema = objectSchema(
                Map.of("dogName", new StringSchema(), "dogOptional", new StringSchema()),
                List.of("dogName"));
        ComposedSchema composedSchema = new ComposedSchema();
        composedSchema.setOneOf(List.of(catSchema, dogSchema));
        FuzzingData data = fuzzingData("{\"dogName\":\"Rex\",\"dogOptional\":\"value\"}", composedSchema, Map.of(),
                List.of("dogName"));

        fuzzer.fuzz(data);

        Assertions.assertThat(JsonUtils.equalAsJson(capturedContext().getPayload(), "{\"dogName\":\"Rex\"}"))
                .isTrue();
    }

    @Test
    void shouldRemoveAFieldThatIsOptionalInTheGeneratedVariant() {
        ObjectSchema schema = objectSchema(
                Map.of("shared", new StringSchema(), "variantRequired", new StringSchema()),
                List.of("shared", "variantRequired"));
        FuzzingData data = fuzzingData("{\"shared\":\"optional here\",\"variantRequired\":\"required\"}", schema,
                Map.of(), List.of("variantRequired"));

        fuzzer.fuzz(data);

        Assertions.assertThat(JsonUtils.equalAsJson(capturedContext().getPayload(),
                "{\"variantRequired\":\"required\"}")).isTrue();
    }

    @Test
    void shouldIgnoreMinPropertiesAndRetainOnlyExplicitlyRequiredProperties() {
        ObjectSchema schema = objectSchema(
                Map.of("required", new StringSchema(), "optional", new StringSchema()),
                List.of("required"));
        schema.setMinProperties(2);
        FuzzingData data = fuzzingData("{\"required\":\"yes\",\"optional\":\"needed by minProperties\"}", schema, Map.of(),
                List.of("required"));

        fuzzer.fuzz(data);

        Assertions.assertThat(JsonUtils.equalAsJson(capturedContext().getPayload(), "{\"required\":\"yes\"}"))
                .isTrue();
    }

    @Test
    void shouldFallBackToOriginalPayloadWhenPayloadIsInvalid() {
        FuzzingData data = fuzzingData("{invalid", new StringSchema(), Map.of(), List.of());

        fuzzer.fuzz(data);

        Assertions.assertThat(capturedContext().getPayload()).isEqualTo("{invalid");
    }

    @Test
    void shouldExposeNameAndDescription() {
        Assertions.assertThat(fuzzer).hasToString("OnlyRequiredFieldsFuzzer");
        Assertions.assertThat(fuzzer.description()).contains("only required fields", "2XX");
    }

    private FuzzingData fuzzingData(String payload, Schema<?> schema, Map<String, Schema> schemaMap,
                                    List<String> requiredFields) {
        return FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/path")
                .payload(payload)
                .reqSchema(schema)
                .schemaMap(schemaMap)
                .allRequiredFields(requiredFields)
                .requestPropertyTypes(Map.of())
                .requestContentTypes(List.of("application/json"))
                .build();
    }

    private ObjectSchema objectSchema(Map<String, Schema> properties, List<String> required) {
        ObjectSchema schema = new ObjectSchema();
        schema.setProperties(properties);
        schema.setRequired(required);
        return schema;
    }

    private SimpleExecutorContext capturedContext() {
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        return captor.getValue();
    }
}
