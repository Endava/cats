package com.endava.cats.fuzzer.http;

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
import com.google.gson.JsonPrimitive;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class FilterParameterHonoredFuzzerTest {
    private RuntimeResourcePool pool;
    private SimpleExecutor executor;
    private TestCaseListener listener;
    private FilterParameterHonoredFuzzer fuzzer;

    @BeforeEach
    void setup() {
        pool = Mockito.mock(RuntimeResourcePool.class);
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
        fuzzer = new FilterParameterHonoredFuzzer(pool, executor, listener);
    }

    @Test
    void shouldWarnWhenFilteredCollectionContainsOtherValues() {
        FuzzingData data = data(Map.of("status", enumSchema("ACTIVE", "CLOSED")));
        fuzzer.fuzz(data);

        SimpleExecutorContext context = captureContext();
        Assertions.assertThat(context.getPayload()).contains("\"status\":\"ACTIVE\"").contains("\"limit\":10");
        Assertions.assertThat(context.getMutationTargets()).containsExactly(RequestTarget.query("status"));
        Assertions.assertThat(context.isReplaceRefData()).as("baseline replay must not overwrite the filter value").isFalse();
        context.getResponseProcessor().accept(response(200,
                "{\"items\":[{\"status\":\"ACTIVE\"},{\"status\":\"CLOSED\"},{\"status\":\"DRAFT\"},{\"status\":\"CLOSED\"}]}"), data);

        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data), Mockito.eq(Reason.FILTER_PARAMETER_IGNORED.value()),
                Mockito.anyString(), Mockito.eq("status"), Mockito.eq("ACTIVE"), Mockito.eq(List.of("CLOSED", "DRAFT")));
    }

    @Test
    void shouldPreferValueObservedInEarlierCollectionResponse() {
        Mockito.when(pool.successfulExchanges("/orders", HttpMethod.GET)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/orders", HttpMethod.GET, "https://api.example.com/orders",
                        "{}", 200, "[{\"owner_id\":42},{\"owner_id\":7}]", null, null)));
        FuzzingData data = data(Map.of("ownerId", new IntegerSchema()));

        fuzzer.fuzz(data);

        SimpleExecutorContext context = captureContext();
        Assertions.assertThat(context.getPayload()).contains("\"ownerId\":42");
        context.getResponseProcessor().accept(response(200, "[{\"owner_id\":42.0},{\"ownerId\":\"42\"},{\"name\":\"x\"}]"), data);
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(),
                Mockito.eq("ownerId"), Mockito.eq("42"), Mockito.eq(2));
    }

    @ParameterizedTest
    @CsvSource(value = {"400|{}|false", "200|{}|false", "200|invalid|false", "200|[]|true", "200|[{\"name\":\"x\"}]|false"}, delimiter = '|')
    void shouldReportInfoWhenInconclusive(int status, String body, boolean truncated) {
        FuzzingData data = data(Map.of("active", new BooleanSchema()));
        fuzzer.fuzz(data);

        captureContext().getResponseProcessor().accept(CatsResponse.builder().responseCode(status).body(body).bodyTruncated(truncated).build(), data);

        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.any(Object[].class));
        Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
    }

    @Test
    void shouldReportServerError() {
        FuzzingData data = data(Map.of("active", new BooleanSchema()));
        fuzzer.fuzz(data);

        captureContext().getResponseProcessor().accept(response(503, "{}"), data);

        Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data), Mockito.eq(Reason.SERVER_ERROR.value()),
                Mockito.anyString(), Mockito.eq("active"), Mockito.eq("true"), Mockito.eq("503"));
    }

    @ParameterizedTest
    @CsvSource({"POST,/orders", "GET,/orders/{id}"})
    void shouldSkipNonCollectionReads(HttpMethod method, String path) {
        fuzzer.fuzz(data(Map.of("status", enumSchema("ACTIVE"))).toBuilder().method(method).path(path).build());

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldSkipWhenNoEligibleFilterParameterExists() {
        Map<String, Schema> types = Map.of("name", new StringSchema(), "limit", new IntegerSchema(),
                "sort", enumSchema("asc", "desc"), "ownerId", new IntegerSchema());
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).path("/orders")
                .payload("{\"name\":\"x\",\"limit\":10,\"sort\":\"asc\",\"ownerId\":1}")
                .queryParams(Set.of("name", "limit", "sort", "ownerId", "missing")).requestPropertyTypes(types).build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @ParameterizedTest
    @CsvSource({"status,true", "active,true", "ownerId,true", "uuid,true", "name,false", "limit,false", "absent,false", "unknown,false"})
    void shouldOnlyTreatExactMatchTypesAsFilters(String param, boolean eligible) {
        Map<String, Schema> types = Map.of("status", enumSchema("A"), "active", new BooleanSchema(), "ownerId", new IntegerSchema(),
                "uuid", new UUIDSchema(), "name", new StringSchema(), "limit", new IntegerSchema(), "absent", new BooleanSchema());
        FuzzingData data = FuzzingData.builder().payload("{\"status\":\"A\",\"active\":true,\"ownerId\":1,\"uuid\":\"u\",\"name\":\"n\",\"limit\":1,\"unknown\":1}")
                .requestPropertyTypes(types).build();

        Assertions.assertThat(FilterParameterHonoredFuzzer.isExactMatchFilter(data, param)).isEqualTo(eligible);
    }

    @ParameterizedTest
    @CsvSource({"5,5.0,true", "ACTIVE, active ,true", "true,TRUE,true", "5,6,false", "a,b,false"})
    void shouldCompareValuesSemantically(String actual, String expected, boolean same) {
        Assertions.assertThat(FilterParameterHonoredFuzzer.sameValue(primitive(actual), primitive(expected))).isEqualTo(same);
    }

    @Test
    void shouldDeriveDeclaredValuesFromContract() {
        IntegerSchema numericEnum = new IntegerSchema();
        numericEnum.setEnum(Arrays.<Number>asList(null, 3));

        Assertions.assertThat(FilterParameterHonoredFuzzer.declaredValue(numericEnum)).contains(new JsonPrimitive(3));
        Assertions.assertThat(FilterParameterHonoredFuzzer.declaredValue(new BooleanSchema())).contains(new JsonPrimitive(true));
        Assertions.assertThat(FilterParameterHonoredFuzzer.declaredValue(new IntegerSchema())).isEmpty();
        Assertions.assertThat(FilterParameterHonoredFuzzer.verdict(JsonUtils.parseAsJsonElement("[1,{\"id\":2}]").getAsJsonArray(),
                new FilterParameterHonoredFuzzer.FilterProbe("id", new JsonPrimitive(BigDecimal.TWO))).compared()).isEqualTo(1);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("FilterParameterHonoredFuzzer");
    }

    private SimpleExecutorContext captureContext() {
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        return captor.getValue();
    }

    private static JsonPrimitive primitive(String raw) {
        return JsonUtils.parseAsJsonElement(raw.matches("-?\\d+(\\.\\d+)?|true|TRUE") ? raw.toLowerCase() : "\"" + raw + "\"").getAsJsonPrimitive();
    }

    private static Schema<String> enumSchema(String... values) {
        StringSchema schema = new StringSchema();
        schema.setEnum(List.of(values));
        return schema;
    }

    private static CatsResponse response(int status, String body) {
        return CatsResponse.builder().responseCode(status).body(body).build();
    }

    private static FuzzingData data(Map<String, Schema> filterTypes) {
        String filter = filterTypes.keySet().iterator().next();
        Map<String, Schema> types = new HashMap<>(filterTypes);
        types.put("limit", new IntegerSchema());
        return FuzzingData.builder().method(HttpMethod.GET).path("/orders").contractPath("/orders")
                .payload("{\"limit\":10,\"" + filter + "\":\"seed\"}")
                .queryParams(Set.of("limit", filter)).requestPropertyTypes(types).build();
    }
}
