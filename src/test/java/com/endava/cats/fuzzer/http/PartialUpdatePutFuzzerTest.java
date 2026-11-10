package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonObject;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@QuarkusTest
class PartialUpdatePutFuzzerTest {
    private RuntimeResourcePool pool;
    private FilterArguments filters;
    private ApiArguments arguments;
    private SimpleExecutor executor;
    private TestCaseListener listener;
    private PartialUpdatePutFuzzer fuzzer;

    @BeforeEach
    void setup() {
        pool = Mockito.mock(RuntimeResourcePool.class);
        filters = Mockito.mock(FilterArguments.class);
        Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(true);
        arguments = new ApiArguments();
        arguments.setServer("https://api.example.com");
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
        fuzzer = new PartialUpdatePutFuzzer(pool, arguments, filters, executor, listener);
    }

    @Test
    void shouldWarnWhenOmittedOptionalFieldRetainsPreviousValueOnPut() {
        ownedPut("{\"name\":\"Updated\",\"description\":\"KeepMe\"}");
        FuzzingData data = putData(List.of("name"));
        List<SimpleExecutorContext> contexts = executeThreeStage(
                data,
                "{\"name\":\"Updated\",\"description\":\"KeepMe\"}",
                "{\"name\":\"Updated\",\"description\":\"KeepMe\"}");

        Assertions.assertThat(contexts).hasSize(3);
        Assertions.assertThat(contexts.get(1).getHttpMethod()).isEqualTo(HttpMethod.PUT);
        Assertions.assertThat(contexts.get(1).getPayload()).isEqualTo("{\"name\":\"Updated\"}");
        Mockito.verify(listener).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq(Reason.PARTIAL_UPDATE_PUT.value()),
                Mockito.anyString(),
                Mockito.eq("/items/1"),
                Mockito.eq(List.of("omitted optional field [description] retained value [KeepMe]")));
    }

    @Test
    void shouldWarnWhenSentSharedFieldDiffersAfterPutWithAllRequiredFields() {
        ownedPut("{\"name\":\"Updated\",\"quantity\":20}");
        FuzzingData data = putDataWithQuantityRequired();
        List<SimpleExecutorContext> contexts = executeThreeStage(
                data,
                "{\"name\":\"Original\",\"quantity\":10}",
                "{\"name\":\"Updated\",\"quantity\":10}");

        Assertions.assertThat(contexts).hasSize(3);
        Assertions.assertThat(contexts.get(1).getPayload()).isEqualTo("{\"name\":\"Updated\",\"quantity\":20}");
        Mockito.verify(listener).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq(Reason.PARTIAL_UPDATE_PUT.value()),
                Mockito.anyString(),
                Mockito.eq("/items/1"),
                Mockito.eq(List.of("sent field [quantity] differs")));
    }

    @Test
    void shouldReportInfoWhenOmittedFieldIsClearedAndSentFieldMatches() {
        ownedPut("{\"name\":\"Updated\",\"description\":\"KeepMe\"}");
        FuzzingData data = putData(List.of("name"));
        executeThreeStage(
                data,
                "{\"name\":\"Updated\",\"description\":\"KeepMe\"}",
                "{\"name\":\"Updated\",\"description\":null}");

        Mockito.verify(listener, Mockito.never()).reportResultWarn(
                Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
        Mockito.verify(listener).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.eq("PUT full-replacement check passed on {}"), Mockito.eq("/items/1"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0|500|{}|false|error|1",
            "0|404|{}|false|info|1",
            "0|200|{}|true|info|1",
            "0|200|invalid|false|info|1",
            "0|200|[]|false|info|1",
            "1|500|{}|false|error|2",
            "1|400|{}|false|info|2",
            "2|500|{}|false|error|3",
            "2|404|{}|false|info|3",
            "2|200|{}|true|info|3",
            "2|200|invalid|false|info|3",
            "2|200|[]|false|info|3",
            "2|200|{\"name\":\"Updated\"}|false|info|3",
            "2|200|{\"name\":\"Updated\",\"description\":\"DifferentDefault\"}|false|info|3"
    }, delimiter = '|')
    void shouldClassifyEachStage(int stage, int status, String body, boolean truncated, String verdict, int expectedCalls) {
        ownedPut("{\"name\":\"Updated\",\"description\":\"KeepMe\"}");
        FuzzingData data = putData(List.of("name"));
        AtomicInteger calls = new AtomicInteger();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext ctx = invocation.getArgument(0);
            int idx = calls.getAndIncrement();
            ctx.getResponseProcessor().accept(CatsResponse.builder()
                    .responseCode(idx == stage ? status : 200)
                    .body(idx == stage ? body : "{\"name\":\"Updated\",\"description\":\"KeepMe\"}")
                    .bodyTruncated(idx == stage && truncated)
                    .build(), data);
            return null;
        }).when(executor).execute(Mockito.any());

        fuzzer.fuzz(data);

        Assertions.assertThat(calls.get()).isEqualTo(expectedCalls);
        if (verdict.equals("error")) {
            Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data),
                    Mockito.eq(Reason.SERVER_ERROR.value()), Mockito.anyString(), Mockito.any());
        } else {
            Mockito.verify(listener, Mockito.never()).reportResultWarn(
                    Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"method", "pathItem", "get", "reqSchema", "filter", "noSharedFields", "noOwnedPut"})
    void shouldSkipWhenOutOfScope(String reason) {
        FuzzingData data = putData(List.of("name"));
        switch (reason) {
            case "method" -> data = data.toBuilder().method(HttpMethod.PATCH).build();
            case "pathItem" -> data = data.toBuilder().pathItem(null).build();
            case "get" -> data = data.toBuilder().pathItem(new PathItem().put(new Operation())).build();
            case "reqSchema" -> data = data.toBuilder().reqSchema(null).build();
            case "filter" -> Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(false);
            case "noSharedFields" -> data = data.toBuilder()
                    .reqSchema(new ObjectSchema().addProperty("onlyInPut", new StringSchema()))
                    .requestPropertyTypes(Map.of("onlyInPut", new StringSchema()))
                    .build();
            default -> { }
        }
        if (!reason.equals("noOwnedPut")) {
            ownedPut("{\"name\":\"Updated\",\"description\":\"KeepMe\"}");
        }

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldFallbackToBaselineFieldsWhenGetDeclaresNoResponseSchema() {
        ownedPut("{\"name\":\"Updated\",\"description\":\"KeepMe\"}");
        ObjectSchema putSchema = new ObjectSchema();
        putSchema.addProperty("name", new StringSchema());
        putSchema.addProperty("description", new StringSchema());
        putSchema.setRequired(List.of("name"));
        FuzzingData data = FuzzingData.builder()
                .path("/items/{id}").contractPath("/items/{id}").method(HttpMethod.PUT)
                .pathItem(new PathItem().get(new Operation()).put(new Operation()))
                .reqSchema(putSchema).requestPropertyTypes(putSchema.getProperties())
                .allRequiredFields(List.of("name")).build();

        List<SimpleExecutorContext> contexts = executeThreeStage(
                data,
                "{\"name\":\"Updated\",\"description\":\"KeepMe\"}",
                "{\"name\":\"Updated\",\"description\":\"KeepMe\"}");

        Assertions.assertThat(contexts).hasSize(3);
        Mockito.verify(listener).reportResultWarn(
                Mockito.any(), Mockito.eq(data), Mockito.eq(Reason.PARTIAL_UPDATE_PUT.value()),
                Mockito.anyString(), Mockito.eq("/items/1"),
                Mockito.eq(List.of("omitted optional field [description] retained value [KeepMe]")));
    }

    @Test
    void shouldExcludeVolatileReadOnlyWriteOnlyIdentifierAndSensitiveFieldsFromSharedSchema() {
        ObjectSchema shared = new ObjectSchema();
        shared.addProperty("id", new IntegerSchema());
        shared.addProperty("userId", new StringSchema());
        shared.addProperty("updatedAt", new StringSchema());
        shared.addProperty("password", new StringSchema());
        shared.addProperty("readOnlyField", new StringSchema().readOnly(true));
        shared.addProperty("writeOnlyField", new StringSchema().writeOnly(true));
        shared.addProperty("title", new StringSchema());

        FuzzingData data = putDataWithSchema(shared, shared, List.of());

        Assertions.assertThat(PartialUpdatePutFuzzer.sharedWritableProperties(data))
                .containsOnlyKeys("title");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "{\"name\":{\"nested\":1}}"})
    void shouldReturnEmptyPlanWhenNoComparablePrimitiveSent(String sentBody) {
        FuzzingData data = putData(List.of("name", "description"));
        Map<String, Schema> shared = PartialUpdatePutFuzzer.sharedWritableProperties(data);
        JsonObject baseline = JsonUtils.parseAsJsonElement("{\"name\":\"x\",\"description\":\"y\"}").getAsJsonObject();

        Assertions.assertThat(PartialUpdatePutFuzzer.buildPlan(
                data, new ObservedHttpTarget.OwnedWrite("/items/1", sentBody), baseline, shared)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":\"x\",\"description\":\"\"}", "{\"name\":\"x\",\"description\":\"false\"}", "{\"name\":\"x\",\"description\":0}"})
    void shouldNotOmitZeroFalseOrBlankBaselineField(String baselineJson) {
        FuzzingData data = putData(List.of("name"));
        Map<String, Schema> shared = PartialUpdatePutFuzzer.sharedWritableProperties(data);
        JsonObject baseline = JsonUtils.parseAsJsonElement(baselineJson).getAsJsonObject();

        var plan = PartialUpdatePutFuzzer.buildPlan(
                data, new ObservedHttpTarget.OwnedWrite("/items/1", "{\"name\":\"x\",\"description\":\"y\"}"), baseline, shared).orElseThrow();

        Assertions.assertThat(plan.omitted()).isNull();
    }

    @Test
    void shouldResolveDefaultResponseSchemaFromOpenApiComponents() {
        Schema<?> itemSchema = new ObjectSchema().addProperty("name", new StringSchema());
        OpenAPI openApi = new OpenAPI().components(new Components()
                .addSchemas("Item", itemSchema)
                .addResponses("ItemResp", new ApiResponse().content(new Content()
                        .addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/Item"))))));
        Operation getOp = new Operation().responses(new ApiResponses()
                .addApiResponse("default", new ApiResponse().$ref("#/components/responses/ItemResp")));
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.PUT).path("/items/{id}").contractPath("/items/{id}")
                .pathItem(new PathItem().get(getOp).put(new Operation()))
                .reqSchema(itemSchema).openApi(openApi).build();

        Assertions.assertThat(PartialUpdatePutFuzzer.sharedWritableProperties(data)).containsOnlyKeys("name");
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("PartialUpdatePutFuzzer");
    }

    private List<SimpleExecutorContext> executeThreeStage(FuzzingData data, String baselineBody, String afterBody) {
        List<SimpleExecutorContext> contexts = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext ctx = invocation.getArgument(0);
            contexts.add(ctx);
            String body = contexts.size() == 1 ? baselineBody : afterBody;
            ctx.getResponseProcessor().accept(CatsResponse.builder().responseCode(200).body(body).build(), data);
            return null;
        }).when(executor).execute(Mockito.any());
        fuzzer.fuzz(data);
        return contexts;
    }

    private void ownedPut(String body) {
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST,
                        "https://api.example.com/items", "{}", 201, "{}", "https://api.example.com/items/1", null)));
        Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PUT)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                        "https://api.example.com/items/1", body, 200, "{}", null, null)));
    }

    private static FuzzingData putData(List<String> required) {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("name", new StringSchema());
        schema.addProperty("description", new StringSchema());
        schema.setRequired(required);
        return putDataWithSchema(schema, schema, required);
    }

    private static FuzzingData putDataWithQuantityRequired() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("name", new StringSchema());
        schema.addProperty("quantity", new IntegerSchema());
        List<String> required = List.of("name", "quantity");
        schema.setRequired(required);
        return putDataWithSchema(schema, schema, required);
    }

    private static FuzzingData putDataWithSchema( ObjectSchema putSchema, ObjectSchema getSchema, List<String> required) {
        Operation getOp = new Operation().responses(new ApiResponses().addApiResponse("200",
                new ApiResponse().content(new Content().addMediaType("application/json", new MediaType().schema(getSchema)))));
        return FuzzingData.builder()
                .path("/items/{id}")
                .contractPath("/items/{id}")
                .method(HttpMethod.PUT)
                .pathItem(new PathItem().get(getOp).put(new Operation()))
                .reqSchema(putSchema)
                .requestPropertyTypes(putSchema.getProperties())
                .allRequiredFields(required)
                .build();
    }
}
