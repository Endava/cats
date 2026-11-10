package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class RejectedUpdateSideEffectsFuzzerTest {
    private static final String OWNED_BODY = "{\"name\":\"Original\",\"quantity\":3,\"code\":\"AB-1\"}";
    private RuntimeResourcePool pool;
    private FilterArguments filters;
    private SimpleExecutor executor;
    private TestCaseListener listener;
    private RejectedUpdateSideEffectsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        pool = Mockito.mock(RuntimeResourcePool.class);
        filters = Mockito.mock(FilterArguments.class);
        Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(true);
        ApiArguments arguments = new ApiArguments();
        arguments.setServer("https://api.example.com");
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
        fuzzer = new RejectedUpdateSideEffectsFuzzer(pool, arguments, filters, executor, listener);
    }

    @Test
    void shouldBuildProbeWithCanaryAndTrailingTypeViolation() {
        List<SimpleExecutorContext> sent = respond(HttpMethod.PATCH, 400, "{\"name\":\"Original\",\"quantity\":3}");

        Assertions.assertThat(sent).extracting(SimpleExecutorContext::getHttpMethod).containsExactly(HttpMethod.GET, HttpMethod.PATCH, HttpMethod.GET);
        SimpleExecutorContext probe = sent.get(1);
        Assertions.assertThat(probe.getPath()).isEqualTo("/items/1");
        Assertions.assertThat(probe.isReplaceRefData()).isFalse();
        Assertions.assertThat(probe.getPayload()).isEqualTo("{\"name\":\"cats-atomic-patch\",\"code\":\"AB-1\",\"quantity\":\"cats-not-a-number\"}");
        Assertions.assertThat(probe.getMutationTargets()).containsExactlyInAnyOrder(RequestTarget.body("name"), RequestTarget.body("quantity"));
        Assertions.assertThat(sent.getLast().getPath()).isEqualTo("/items/1");
    }

    @ParameterizedTest
    @CsvSource({"PATCH,error", "PUT,warn"})
    void shouldReportPersistedCanaryAfterRejection(HttpMethod method, String verdict) {
        respond(method, 422, "{\"name\":\" CATS-ATOMIC-" + method + " \",\"quantity\":3}");

        if (verdict.equals("error")) {
            Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq(Reason.REJECTED_UPDATE_PARTIALLY_APPLIED.value()),
                    Mockito.anyString(), Mockito.eq(method), Mockito.eq("quantity"), Mockito.eq("name"), Mockito.eq("cats-atomic-patch"));
        } else {
            Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.eq(Reason.REJECTED_UPDATE_PARTIALLY_APPLIED.value()),
                    Mockito.anyString(), Mockito.eq(method), Mockito.eq("quantity"), Mockito.eq("name"), Mockito.eq("cats-atomic-put"));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0|500|{}|false|error|1",
            "0|404|{}|false|info|1",
            "0|200|[]|false|info|1",
            "0|200|{\"name\":\"cats-atomic-patch\"}|false|info|1",
            "1|500|{}|false|error|2",
            "1|200|{}|false|info|2",
            "1|404|{}|false|info|2",
            "2|500|{}|false|error|3",
            "2|404|{}|false|info|3",
            "2|200|invalid|false|info|3",
            "2|200|[]|false|info|3",
            "2|200|{\"name\":\"cats-atomic-patch\"}|true|info|3",
            "2|200|{\"name\":\"Original\"}|false|info|3"}, delimiter = '|')
    void shouldClassifyEveryStage(int stage, int status, String body, boolean truncated, String verdict, int calls) {
        ownedWrite(HttpMethod.PATCH);
        FuzzingData data = data(HttpMethod.PATCH);
        List<SimpleExecutorContext> sent = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext context = invocation.getArgument(0);
            sent.add(context);
            boolean current = sent.size() - 1 == stage;
            int defaultStatus = context.getHttpMethod() == HttpMethod.GET ? 200 : 400;
            context.getResponseProcessor().accept(CatsResponse.builder().responseCode(current ? status : defaultStatus)
                    .body(current ? body : "{\"name\":\"Original\"}").bodyTruncated(current && truncated).build(), data);
            return null;
        }).when(executor).execute(Mockito.any());

        fuzzer.fuzz(data);

        Assertions.assertThat(sent).hasSize(calls);
        if (verdict.equals("error")) {
            Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data), Mockito.eq(Reason.SERVER_ERROR.value()),
                    Mockito.anyString(), Mockito.any(Object[].class));
        } else {
            Mockito.verify(listener, Mockito.atLeastOnce()).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.any(Object[].class));
        }
        Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"method", "pathItem", "get", "filter", "notOwned"})
    void shouldSkipWhenOutOfScope(String reason) {
        FuzzingData data = data(HttpMethod.PATCH);
        switch (reason) {
            case "method" -> data = data(HttpMethod.POST);
            case "pathItem" -> data = data.toBuilder().pathItem(null).build();
            case "get" -> data = data.toBuilder().pathItem(new PathItem().patch(new Operation())).build();
            case "filter" -> Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(false);
            default -> Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PATCH)).thenReturn(List.of(write(HttpMethod.PATCH)));
        }
        if (!reason.equals("notOwned")) {
            ownedWrite(HttpMethod.PATCH);
        }

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{\"quantity\":3}", "{\"name\":\"x\"}", "{\"name\":1,\"quantity\":3}"})
    void shouldNotBuildProbeWithoutBothCandidates(String body) {
        Assertions.assertThat(RejectedUpdateSideEffectsFuzzer.buildProbe(data(HttpMethod.PATCH),
                new ObservedHttpTarget.OwnedWrite("/items/1", body))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"readOnly", "writeOnly", "enum", "pattern", "format", "maxLength", "minLength", "missing", "volatile"})
    void shouldNotUseConstrainedStringsAsCanary(String constraint) {
        String field = constraint.equals("volatile") ? "updatedAt" : "name";
        Map<String, Schema> types = new HashMap<>(Map.of("quantity", new IntegerSchema()));
        StringSchema schema = new StringSchema();
        switch (constraint) {
            case "readOnly" -> schema.setReadOnly(true);
            case "writeOnly" -> schema.setWriteOnly(true);
            case "enum" -> schema.setEnum(List.of("a", "b"));
            case "pattern" -> schema.setPattern("^[a-z]+$");
            case "format" -> schema.setFormat("email");
            case "maxLength" -> schema.setMaxLength(5);
            case "minLength" -> schema.setMinLength(50);
            default -> { }
        }
        if (!constraint.equals("missing")) {
            types.put(field, schema);
        }
        FuzzingData data = data(HttpMethod.PATCH).toBuilder().requestPropertyTypes(types).build();

        Assertions.assertThat(RejectedUpdateSideEffectsFuzzer.buildProbe(data,
                new ObservedHttpTarget.OwnedWrite("/items/1", "{\"" + field + "\":\"x\",\"quantity\":3}"))).isEmpty();
    }

    @Test
    void shouldUseBooleanViolationForBooleanFields() {
        FuzzingData data = data(HttpMethod.PUT).toBuilder()
                .requestPropertyTypes(Map.of("name", new StringSchema(), "active", new BooleanSchema())).build();

        var probe = RejectedUpdateSideEffectsFuzzer.buildProbe(data,
                new ObservedHttpTarget.OwnedWrite("/items/1", "{\"active\":true,\"name\":\"x\"}")).orElseThrow();

        Assertions.assertThat(JsonUtils.parseAsJsonElement(probe.payload()).getAsJsonObject().get("active").getAsString())
                .isEqualTo("cats-not-a-boolean");
        Assertions.assertThat(probe.fields().canaryValue()).isEqualTo("cats-atomic-put");
    }

    @ParameterizedTest
    @CsvSource({"400,true", "401,true", "409,true", "422,true", "404,false", "410,false", "200,false", "500,false"})
    void shouldOnlyEvaluateClientRejectionsThatKeepTheResource(int code, boolean evaluable) {
        Assertions.assertThat(RejectedUpdateSideEffectsFuzzer.isEvaluableRejection(code)).isEqualTo(evaluable);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("RejectedUpdateSideEffectsFuzzer");
    }

    private List<SimpleExecutorContext> respond(HttpMethod method, int probeStatus, String getBody) {
        ownedWrite(method);
        FuzzingData data = data(method);
        List<SimpleExecutorContext> sent = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext context = invocation.getArgument(0);
            sent.add(context);
            boolean read = context.getHttpMethod() == HttpMethod.GET;
            String body = sent.size() == 1 ? "{\"name\":\"Original\",\"quantity\":3}" : getBody;
            context.getResponseProcessor().accept(CatsResponse.builder().responseCode(read ? 200 : probeStatus)
                    .body(read ? body : "{}").build(), data);
            return null;
        }).when(executor).execute(Mockito.any());
        fuzzer.fuzz(data);
        return sent;
    }

    private void ownedWrite(HttpMethod method) {
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST, "https://api.example.com/items",
                        "{}", 201, "{}", "https://api.example.com/items/1", null)));
        Mockito.when(pool.successfulExchanges("/items/{id}", method)).thenReturn(List.of(write(method)));
    }

    private static RuntimeResourcePool.SuccessfulExchange write(HttpMethod method) {
        return new RuntimeResourcePool.SuccessfulExchange("/items/{id}", method, "https://api.example.com/items/1",
                OWNED_BODY, 200, "{}", null, null);
    }

    private static FuzzingData data(HttpMethod method) {
        return FuzzingData.builder().method(method).path("/items/{id}").contractPath("/items/{id}")
                .pathItem(new PathItem().get(new Operation()).put(new Operation()).patch(new Operation()))
                .requestPropertyTypes(Map.of("name", new StringSchema(), "quantity", new IntegerSchema(),
                        "code", new StringSchema().pattern("^[A-Z]{2}-\\d$")))
                .build();
    }
}
