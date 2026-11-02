package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class SmallNegativeAndInt32BoundaryFieldsFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private ProcessingArguments processingArguments;
    private SmallNegativeAndInt32BoundaryFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        fuzzer = new SmallNegativeAndInt32BoundaryFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldFuzzIntegerFieldsWithZeroMinusOneAndMaxInt32() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(true);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"page\":1,\"price\":9.5,\"name\":\"item\"}");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(data.getPath()).thenReturn("/items");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("page", "price", "name"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of(
                "page", new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.TEN),
                "price", new NumberSchema(),
                "name", new StringSchema()));

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor =
                ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(6)).execute(captor.capture());
        // 'page' has [1, 10] bounds, so 0, -1, and Integer.MAX_VALUE all violate schema bounds -> FOURXX
        // 'price' has no bounds, so 0, -1, and Integer.MAX_VALUE are valid -> FOURXX_TWOXX
        Assertions.assertThat(captor.getAllValues())
                .filteredOn(ctx -> ctx.getScenario().contains("field [page]"))
                .allMatch(ctx -> ctx.getExpectedResponseCode() == ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(captor.getAllValues())
                .filteredOn(ctx -> ctx.getScenario().contains("field [price]"))
                .allMatch(ctx -> ctx.getExpectedResponseCode() == ResponseCodeFamilyPredefined.FOURXX_TWOXX);
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("SmallNegativeAndInt32BoundaryFieldsFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).containsExactly(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldEvaluateNonStrictModeAndExclusiveBoundsAndBlockingQueryParams() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        FuzzingData nonStrictData = Mockito.mock(FuzzingData.class);
        Mockito.when(nonStrictData.getPayload()).thenReturn("{\"page\":1}");
        Mockito.when(nonStrictData.getAllFieldsByHttpMethod()).thenReturn(Set.of("page"));
        Mockito.when(nonStrictData.getRequestPropertyTypes()).thenReturn(Map.of("page",
                new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.TEN)));

        fuzzer.fuzz(nonStrictData);

        ArgumentCaptor<SimpleExecutorContext> nonStrict = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(nonStrict.capture());
        Assertions.assertThat(nonStrict.getAllValues()).allSatisfy(ctx ->
                Assertions.assertThat(ctx.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_TWOXX));
        Assertions.assertThat(nonStrict.getAllValues()).extracting(ctx ->
                JsonParser.parseString(ctx.getPayload()).getAsJsonObject().get("page").getAsInt())
                .containsExactlyInAnyOrder(0, -1, Integer.MAX_VALUE);

        Mockito.reset(simpleExecutor);
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(true);

        Schema<?> exclusiveMinMax = new IntegerSchema()
                .minimum(BigDecimal.ZERO)
                .exclusiveMinimum(true)
                .maximum(BigDecimal.valueOf(Integer.MAX_VALUE))
                .exclusiveMaximum(true);
        Schema<?> oas31Exclusive = new IntegerSchema()
                .exclusiveMinimumValue(BigDecimal.ZERO)
                .exclusiveMaximumValue(BigDecimal.valueOf(100));

        FuzzingData strictData = Mockito.mock(FuzzingData.class);
        Mockito.when(strictData.getPayload()).thenReturn("{\"a\":1,\"b\":2,\"timeout\":5}");
        Mockito.when(strictData.getQueryParams()).thenReturn(Set.of("timeout"));
        Mockito.when(strictData.getAllFieldsByHttpMethod()).thenReturn(Set.of("a", "b", "timeout", "missing"));
        Mockito.when(strictData.getRequestPropertyTypes()).thenReturn(Map.of(
                "a", exclusiveMinMax,
                "b", oas31Exclusive,
                "timeout", new IntegerSchema(),
                "missing", new IntegerSchema()));

        fuzzer.fuzz(strictData);

        ArgumentCaptor<SimpleExecutorContext> strict = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(6)).execute(strict.capture());
        Assertions.assertThat(strict.getAllValues()).allSatisfy(ctx -> {
            Assertions.assertThat(ctx.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
            Assertions.assertThat(JsonParser.parseString(ctx.getPayload()).getAsJsonObject().get("timeout").getAsInt()).isEqualTo(5);
            Assertions.assertThat(ctx.getMutationTargets()).noneMatch(target -> List.of("timeout", "missing").contains(target.name()));
        });
        for (String field : List.of("a", "b")) {
            Assertions.assertThat(strict.getAllValues()).filteredOn(ctx -> ctx.getScenario().contains("field [" + field + "]"))
                    .extracting(ctx -> JsonParser.parseString(ctx.getPayload()).getAsJsonObject().get(field).getAsInt())
                    .containsExactlyInAnyOrder(0, -1, Integer.MAX_VALUE);
        }
        Mockito.clearInvocations(simpleExecutor);
        fuzzer.fuzz(FuzzingData.builder().payload("").build());
        fuzzer.fuzz(FuzzingData.builder().payload("{\"name\":\"abc\"}")
                .allFieldsByHttpMethod(Set.of("name"))
                .requestPropertyTypes(Map.of("name", new StringSchema()))
                .build());
        Mockito.verifyNoInteractions(simpleExecutor);
    }
}
