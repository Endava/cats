package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonObject;
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

class FreeFormObjectFieldsFuzzerTest {
    private static final int MUTATION_COUNT = 13;

    private SimpleExecutor simpleExecutor;
    private FreeFormObjectFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(42);
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        ProcessingArguments processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(processingArguments, "largeStringsSize", 64);
        fuzzer = new FreeFormObjectFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldFuzzNestedObjectsWithAdditionalPropertiesTrue() {
        Schema<?> schema = new Schema<>().type("object").additionalProperties(true);
        FuzzingData data = fuzzingData("""
                {"container":{"metadata":{"existing":"value"}},"fixed":"unchanged"}
                """, "container#metadata", schema);

        fuzzer.fuzz(data);

        List<SimpleExecutorContext> contexts = capturedContexts(MUTATION_COUNT);
        Assertions.assertThat(contexts).allSatisfy(context -> {
            Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_TWOXX);
            Assertions.assertThat(context.isMatchResponseResult()).isFalse();
            Assertions.assertThat(context.isMatchResponseContentType()).isFalse();
            JsonObject payload = JsonUtils.parseAsJsonElement(context.getPayload()).getAsJsonObject();
            Assertions.assertThat(payload.get("fixed").getAsString()).isEqualTo("unchanged");
            Assertions.assertThat(payload.getAsJsonObject("container")
                    .getAsJsonObject("metadata").get("existing").getAsString()).isEqualTo("value");
        });

        SimpleExecutorContext longKey = contextContaining(contexts, "extremely long property name");
        Set<String> keys = longKey.getPayload() == null ? Set.of() : JsonUtils.parseAsJsonElement(longKey.getPayload())
                .getAsJsonObject().getAsJsonObject("container").getAsJsonObject("metadata").keySet();
        Assertions.assertThat(keys).anyMatch(key -> key.length() == 64);

        SimpleExecutorContext reserved = contextContaining(contexts, "prototype-pollution");
        JsonObject reservedObject = JsonUtils.parseAsJsonElement(reserved.getPayload()).getAsJsonObject()
                .getAsJsonObject("container").getAsJsonObject("metadata");
        Assertions.assertThat(reservedObject.keySet())
                .contains("__proto__", "constructor", "prototype", "$where");
    }

    @Test
    void shouldFuzzObjectsWithAnEmptyAdditionalPropertiesSchema() {
        Schema<?> schema = new OpenAPIV3Parser().readContents("""
                        openapi: 3.0.3
                        info:
                          title: Free-form object test
                          version: 1.0.0
                        paths: {}
                        components:
                          schemas:
                            FreeForm:
                              type: object
                              additionalProperties: {}
                        """, null, null)
                .getOpenAPI().getComponents().getSchemas().get("FreeForm");
        FuzzingData data = fuzzingData("{\"metadata\":{}}", "metadata", schema);

        fuzzer.fuzz(data);

        capturedContexts(MUTATION_COUNT);
    }

    @Test
    void shouldFuzzAReferencedRootFreeFormObject() {
        Schema<?> reference = new Schema<>().$ref("#/components/schemas/FreeForm");
        Schema<?> freeForm = new Schema<>().type("object").additionalProperties(true);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"existing\":true}");
        Mockito.when(data.getReqSchema()).thenReturn(reference);
        Mockito.when(data.getSchemaMap()).thenReturn(Map.of("FreeForm", freeForm));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of());

        fuzzer.fuzz(data);

        List<SimpleExecutorContext> contexts = capturedContexts(MUTATION_COUNT);
        Assertions.assertThat(contexts).allSatisfy(context -> Assertions.assertThat(
                JsonUtils.parseAsJsonElement(context.getPayload()).getAsJsonObject().get("existing").getAsBoolean()).isTrue());
    }

    @Test
    void shouldNotFuzzTypedForbiddenOrUnspecifiedAdditionalProperties() {
        FuzzingData typed = fuzzingData("{\"metadata\":{}}", "metadata",
                new Schema<>().type("object").additionalProperties(new StringSchema()));
        FuzzingData forbidden = fuzzingData("{\"metadata\":{}}", "metadata",
                new Schema<>().type("object").additionalProperties(false));
        FuzzingData unspecified = fuzzingData("{\"metadata\":{}}", "metadata", new Schema<>().type("object"));

        fuzzer.fuzz(typed);
        fuzzer.fuzz(forbidden);
        fuzzer.fuzz(unspecified);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldSkipInvalidJsonAndNonObjectValues() {
        FuzzingData invalidJson = fuzzingData("not-json", "metadata",
                new Schema<>().type("object").additionalProperties(true));
        FuzzingData array = fuzzingData("{\"metadata\":[]}", "metadata",
                new Schema<>().type("object").additionalProperties(true));

        fuzzer.fuzz(invalidJson);
        fuzzer.fuzz(array);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldExposeNameDescriptionAndSkippedMethods() {
        Assertions.assertThat(fuzzer).hasToString("FreeFormObjectFieldsFuzzer");
        Assertions.assertThat(fuzzer.description()).contains("additionalProperties", "2XX", "4XX");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    private FuzzingData fuzzingData(String payload, String field, Schema<?> schema) {
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

    private static SimpleExecutorContext contextContaining(List<SimpleExecutorContext> contexts, String scenarioPart) {
        return contexts.stream()
                .filter(context -> context.getScenario().contains(scenarioPart))
                .findFirst()
                .orElseThrow();
    }
}
