package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class FreeFormArrayItemsFieldsFuzzerTest {
    private static final int MUTATION_COUNT = 8;

    private SimpleExecutor simpleExecutor;
    private FreeFormArrayItemsFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(42);
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        ProcessingArguments processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(processingArguments, "largeStringsSize", 64);
        fuzzer = new FreeFormArrayItemsFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldFuzzNestedArraysWithFreeFormItems() {
        ArraySchema schema = new ArraySchema().items(new Schema<>());
        FuzzingData data = fuzzingData("""
                {"container":{"items":["original",2]},"fixed":true}
                """, "container#items", schema);

        fuzzer.fuzz(data);

        List<SimpleExecutorContext> contexts = capturedContexts(MUTATION_COUNT);
        Assertions.assertThat(contexts).isNotEmpty().allSatisfy(context -> {
            Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(
                    ResponseCodeFamilyPredefined.FOURXX_TWOXX);
            Assertions.assertThat(context.isMatchResponseResult()).isFalse();
            Assertions.assertThat(context.isMatchResponseContentType()).isFalse();
            var payload = JsonUtils.parseAsJsonElement(context.getPayload()).getAsJsonObject();
            Assertions.assertThat(payload.get("fixed").getAsBoolean()).isTrue();
            JsonArray items = payload.getAsJsonObject("container").getAsJsonArray("items");
            Assertions.assertThat(items).hasSize(2);
            Assertions.assertThat(items.get(1).getAsInt()).isEqualTo(2);
        });

        SimpleExecutorContext reserved = contextContaining(contexts, "reserved-key object");
        var reservedValue = JsonUtils.parseAsJsonElement(reserved.getPayload()).getAsJsonObject()
                .getAsJsonObject("container").getAsJsonArray("items").get(0).getAsJsonObject();
        Assertions.assertThat(reservedValue.keySet())
                .contains("__proto__", "constructor", "prototype", "$where");
    }

    @Test
    void shouldRecognizeItemsEmptyObjectFromAParsedContract() {
        ArraySchema schema = (ArraySchema) new OpenAPIV3Parser().readContents("""
                        openapi: 3.0.3
                        info:
                          title: Free-form array test
                          version: 1.0.0
                        paths: {}
                        components:
                          schemas:
                            FreeFormArray:
                              type: array
                              items: {}
                        """, null, null)
                .getOpenAPI().getComponents().getSchemas().get("FreeFormArray");
        FuzzingData data = fuzzingData("{\"items\":[true]}", "items", schema);

        fuzzer.fuzz(data);

        capturedContexts(MUTATION_COUNT);
    }

    @Test
    void shouldFuzzAReferencedRootArray() {
        Schema<?> reference = new Schema<>().$ref("#/components/schemas/FreeFormArray");
        ArraySchema freeFormArray = new ArraySchema().items(new Schema<>());
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("[\"existing\",42]");
        Mockito.when(data.getReqSchema()).thenReturn(reference);
        Mockito.when(data.getSchemaMap()).thenReturn(Map.of("FreeFormArray", freeFormArray));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of());

        fuzzer.fuzz(data);

        List<SimpleExecutorContext> contexts = capturedContexts(MUTATION_COUNT);
        Assertions.assertThat(contexts).isNotEmpty().allSatisfy(context -> {
            JsonArray payload = JsonUtils.parseAsJsonElement(context.getPayload()).getAsJsonArray();
            Assertions.assertThat(payload).hasSize(2);
            Assertions.assertThat(payload.get(1).getAsInt()).isEqualTo(42);
        });
    }

    @Test
    void shouldAddAnItemToAnEmptyArrayWhenAllowed() {
        ArraySchema schema = new ArraySchema().items(new Schema<>());
        schema.setMaxItems(1);
        FuzzingData data = fuzzingData("{\"items\":[]}", "items", schema);

        fuzzer.fuzz(data);

        Assertions.assertThat(capturedContexts(MUTATION_COUNT)).isNotEmpty().allSatisfy(context -> Assertions.assertThat(
                        JsonUtils.parseAsJsonElement(context.getPayload()).getAsJsonObject().getAsJsonArray("items"))
                .hasSize(1));
    }

    @Test
    void shouldSkipConstrainedMissingAndImmutableItemArrays() {
        FuzzingData typed = fuzzingData("{\"items\":[\"value\"]}", "items",
                new ArraySchema().items(new StringSchema()));
        FuzzingData missing = fuzzingData("{\"items\":[\"value\"]}", "items", new ArraySchema());
        FuzzingData booleanFalse = fuzzingData("{\"items\":[\"value\"]}", "items",
                new ArraySchema().items(new Schema<>().booleanSchemaValue(false)));
        ArraySchema immutableEmptySchema = new ArraySchema().items(new Schema<>());
        immutableEmptySchema.setMaxItems(0);
        FuzzingData immutableEmpty = fuzzingData("{\"items\":[]}", "items", immutableEmptySchema);

        fuzzer.fuzz(typed);
        fuzzer.fuzz(missing);
        fuzzer.fuzz(booleanFalse);
        fuzzer.fuzz(immutableEmpty);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldSkipInvalidJsonAndNonArrayValues() {
        ArraySchema schema = new ArraySchema().items(new Schema<>());

        fuzzer.fuzz(fuzzingData("not-json", "items", schema));
        fuzzer.fuzz(fuzzingData("{\"items\":{}}", "items", schema));

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldExposeNameDescriptionAndSkippedMethods() {
        Assertions.assertThat(fuzzer).hasToString("FreeFormArrayItemsFieldsFuzzer");
        Assertions.assertThat(fuzzer.description()).contains("unconstrained items", "2XX", "4XX");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    private FuzzingData fuzzingData(String payload, String field, ArraySchema schema) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn(payload);
        Mockito.when(data.getReqSchema()).thenReturn(new Schema<>().type("object"));
        Mockito.when(data.getSchemaMap()).thenReturn(Map.of());
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of(field, schema));
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of(field));
        return data;
    }

    private List<SimpleExecutorContext> capturedContexts(int count) {
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(count)).execute(captor.capture());
        return captor.getAllValues();
    }

    private static SimpleExecutorContext contextContaining(List<SimpleExecutorContext> contexts,
                                                           String scenarioPart) {
        return contexts.stream()
                .filter(context -> context.getScenario().contains(scenarioPart))
                .findFirst()
                .orElseThrow();
    }
}
