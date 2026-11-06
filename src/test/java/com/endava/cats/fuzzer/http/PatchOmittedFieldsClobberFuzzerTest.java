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
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
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
class PatchOmittedFieldsClobberFuzzerTest {
    private RuntimeResourcePool pool;
    private FilterArguments filters;
    private ApiArguments arguments;
    private SimpleExecutor executor;
    private TestCaseListener listener;
    private PatchOmittedFieldsClobberFuzzer fuzzer;

    @BeforeEach
    void setup() {
        pool = Mockito.mock(RuntimeResourcePool.class);
        filters = Mockito.mock(FilterArguments.class);
        Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(true);
        arguments = new ApiArguments();
        arguments.setServer("https://api.example.com");
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
        fuzzer = new PatchOmittedFieldsClobberFuzzer(pool, arguments, filters, executor, listener);
    }

    @Test
    void shouldWarnWhenPartialPatchWipesOmittedOptionalField() {
        FuzzingData data = patchData();
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST,
                        "https://api.example.com/items", "{}", 201, "{}", "https://api.example.com/items/1", null)));
        Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PATCH)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH,
                        "https://api.example.com/items/1", "{\"name\":\"Updated\",\"description\":\"KeepMe\"}", 200, "{}", null, null)));

        List<SimpleExecutorContext> contexts = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext ctx = invocation.getArgument(0);
            contexts.add(ctx);
            if (contexts.size() == 1) {
                ctx.getResponseProcessor().accept(CatsResponse.builder()
                        .responseCode(200)
                        .body("{\"name\":\"Updated\",\"description\":\"KeepMe\"}")
                        .build(), data);
            } else if (contexts.size() == 2) {
                ctx.getResponseProcessor().accept(CatsResponse.builder()
                        .responseCode(200)
                        .body("{\"name\":\"Updated\",\"description\":null}")
                        .build(), data);
            } else if (contexts.size() == 3) {
                ctx.getResponseProcessor().accept(CatsResponse.builder()
                        .responseCode(200)
                        .body("{\"name\":\"Updated\",\"description\":null}")
                        .build(), data);
            }
            return null;
        }).when(executor).execute(Mockito.any());

        fuzzer.fuzz(data);

        Assertions.assertThat(contexts).hasSize(3);
        Assertions.assertThat(contexts.get(1).getHttpMethod()).isEqualTo(HttpMethod.PATCH);
        Assertions.assertThat(contexts.get(1).getPayload()).doesNotContain("description").contains("name");

        Mockito.verify(listener).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq(Reason.PATCH_CLOBBERED_OMITTED_FIELDS.value()),
                Mockito.anyString(),
                Mockito.eq("description"),
                Mockito.eq("KeepMe"));
    }

    @Test
    void shouldReportInfoWhenOmittedFieldIsPreserved() {
        FuzzingData data = patchData();
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST,
                        "https://api.example.com/items", "{}", 201, "{}", "https://api.example.com/items/1", null)));
        Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PATCH)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH,
                        "https://api.example.com/items/1", "{\"name\":\"Updated\",\"description\":\"KeepMe\"}", 200, "{}", null, null)));

        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext ctx = invocation.getArgument(0);
            ctx.getResponseProcessor().accept(CatsResponse.builder()
                    .responseCode(200)
                    .body("{\"name\":\"Updated\",\"description\":\"KeepMe\"}")
                    .build(), data);
            return null;
        }).when(executor).execute(Mockito.any());

        fuzzer.fuzz(data);

        Mockito.verify(listener, Mockito.never()).reportResultWarn(
                Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
    }

    @Test
    void shouldSkipWhenNoOwnedPatchObserved() {
        fuzzer.fuzz(patchData());
        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("PatchOmittedFieldsClobberFuzzer");
    }

    private static FuzzingData patchData() {
        PathItem item = new PathItem().get(new Operation()).patch(new Operation());
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("name", new StringSchema());
        schema.addProperty("description", new StringSchema());
        schema.setRequired(List.of("name"));
        return FuzzingData.builder()
                .path("/items/{id}")
                .contractPath("/items/{id}")
                .method(HttpMethod.PATCH)
                .pathItem(item)
                .reqSchema(schema)
                .requestPropertyTypes(Map.of("name", new StringSchema(), "description", new StringSchema()))
                .allRequiredFields(List.of("name"))
                .build();
    }

    private void ownedPatch() {
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST, "https://api.example.com/items", "{}", 201, "{}", "https://api.example.com/items/1", null)));
        Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PATCH)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH, "https://api.example.com/items/1",
                        "{\"name\":\"Updated\",\"description\":\"KeepMe\"}", 200, "{}", null, null)));
    }

    @ParameterizedTest
    @CsvSource(value = {"0|500|{}|false|error", "0|400|{}|false|info", "0|200|{}|true|info", "0|200|invalid|false|info", "0|200|[]|false|info", "1|500|{}|false|error", "1|400|{}|false|info", "2|500|{}|false|error", "2|400|{}|false|info", "2|200|{}|true|info", "2|200|invalid|false|info", "2|200|[]|false|info", "2|200|{}|false|warn", "2|200|{\"description\":\" \"}|false|warn", "2|200|{\"description\":{}}|false|info", "2|200|{\"description\":\"KeepMe\"}|false|info"}, delimiter = '|')
    void shouldClassifyEachStageAndStopOnUninspectableBaselineOrFailedPatch(int stage, int status, String body, boolean truncated, String result) {
        ownedPatch();
        FuzzingData data = patchData();
        AtomicInteger calls = new AtomicInteger();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext context = invocation.getArgument(0);
            int index = calls.getAndIncrement();
            context.getResponseProcessor().accept(CatsResponse.builder().responseCode(index == stage ? status : 200)
                    .body(index == stage ? body : "{\"name\":\"Updated\",\"description\":\"KeepMe\"}")
                    .bodyTruncated(index == stage && truncated).build(), data);
            return null;
        }).when(executor).execute(Mockito.any());
        fuzzer.fuzz(data);
        Assertions.assertThat(calls.get()).isEqualTo(stage + 1);
        if (result.equals("error")) {
            Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data), Mockito.eq(Reason.SERVER_ERROR.value()), Mockito.anyString(), Mockito.any(Object[].class));
        } else if (result.equals("warn")) {
            Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data), Mockito.eq(Reason.PATCH_CLOBBERED_OMITTED_FIELDS.value()), Mockito.anyString(), Mockito.eq("description"), Mockito.eq("KeepMe"));
        } else {
            Mockito.verify(listener, Mockito.atLeastOnce()).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.any(Object[].class));
            Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"method", "pathItem", "get", "schema", "filter"})
    void shouldSkipWhenReadOrSchemaIsOutOfScope(String missing) {
        FuzzingData data = patchData();
        switch (missing) {
            case "method" -> data = data.toBuilder().method(HttpMethod.PUT).build();
            case "pathItem" -> data = data.toBuilder().pathItem(null).build();
            case "get" -> data = data.toBuilder().pathItem(new PathItem()).build();
            case "schema" -> data = data.toBuilder().reqSchema(null).build();
            default -> Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(false);
        }
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(executor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{}", "{\"name\":\"x\"}", "{\"name\":\"x\",\"description\":null}", "{\"name\":\"x\",\"description\":{}}"})
    void shouldRejectBodiesWithoutOmittablePrimitive(String body) {
        var observed = new ObservedHttpTarget.OwnedWrite("/items/1", body);
        Assertions.assertThat(PatchOmittedFieldsClobberFuzzer.buildPartialPatchPlan(patchData(), observed,
                JsonUtils.parseAsJsonElement("{\"name\":\"x\",\"description\":\"KeepMe\"}").getAsJsonObject())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"description\":null}", "{\"description\":{}}", "{\"description\":\" \"}"})
    void shouldRejectUnpopulatedBaselineFields(String baseline) {
        Assertions.assertThat(PatchOmittedFieldsClobberFuzzer.buildPartialPatchPlan(patchData(),
                new ObservedHttpTarget.OwnedWrite("/items/1", "{\"name\":\"x\",\"description\":\"x\"}"),
                JsonUtils.parseAsJsonElement(baseline).getAsJsonObject())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"readOnly", "writeOnly", "required", "volatile"})
    void shouldPreserveProtectedFields(String protection) {
        FuzzingData data = patchData();
        String field = protection.equals("volatile") ? "updatedAt" : "description";
        String body = "{\"name\":\"x\",\"" + field + "\":\"x\"}";
        StringSchema property = new StringSchema();
        if (protection.equals("readOnly")) property.setReadOnly(true);
        if (protection.equals("writeOnly")) property.setWriteOnly(true);
        if (protection.equals("required")) data = data.toBuilder().allRequiredFields(List.of("name", "description")).build();
        data = data.toBuilder().requestPropertyTypes(Map.of(field, property)).build();
        Assertions.assertThat(PatchOmittedFieldsClobberFuzzer.buildPartialPatchPlan(data,
                new ObservedHttpTarget.OwnedWrite("/items/1", body),
                JsonUtils.parseAsJsonElement(body).getAsJsonObject())).isEmpty();
    }

    @Test
    void shouldStopWhenNoPlanCanBeBuiltFromBaseline() {
        ownedPatch();
        FuzzingData data = patchData();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext context = invocation.getArgument(0);
            context.getResponseProcessor().accept(CatsResponse.builder().responseCode(200).body("{}").build(), data);
            return null;
        }).when(executor).execute(Mockito.any());
        fuzzer.fuzz(data);
        Mockito.verify(executor).execute(Mockito.any());
    }


    @Test
    void shouldBuildPlanWithoutPropertyMetadataAndLeaveObservedBodyIntact() {
        FuzzingData data = patchData().toBuilder().reqSchema(null).requestPropertyTypes(null).allRequiredFields(null).build();
        String body = "{\"description\":\"KeepMe\",\"name\":\"Updated\"}";
        var baseline = JsonUtils.parseAsJsonElement(body).getAsJsonObject();
        var plan = PatchOmittedFieldsClobberFuzzer.buildPartialPatchPlan(data, new ObservedHttpTarget.OwnedWrite("/items/1", body), baseline).orElseThrow();
        Assertions.assertThat(plan.omittedField()).isEqualTo("description");
        Assertions.assertThat(plan.baselineValue()).isEqualTo("KeepMe");
        Assertions.assertThat(plan.partialPayload()).isEqualTo("{\"name\":\"Updated\"}");
        Assertions.assertThat(baseline.has("description")).isTrue();
    }

}
