package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
                "page", new IntegerSchema().minimum(java.math.BigDecimal.ONE).maximum(java.math.BigDecimal.TEN),
                "price", new io.swagger.v3.oas.models.media.NumberSchema(),
                "name", new io.swagger.v3.oas.models.media.StringSchema()));

        fuzzer.fuzz(data);

        org.mockito.ArgumentCaptor<com.endava.cats.fuzzer.executor.SimpleExecutorContext> captor =
                org.mockito.ArgumentCaptor.forClass(com.endava.cats.fuzzer.executor.SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(6)).execute(captor.capture());
        // 'page' has [1, 10] bounds, so 0, -1, and Integer.MAX_VALUE all violate schema bounds -> FOURXX
        // 'price' has no bounds, so 0, -1, and Integer.MAX_VALUE are valid -> FOURXX_TWOXX
        Assertions.assertThat(captor.getAllValues())
                .filteredOn(ctx -> ctx.getScenario().contains("field [page]"))
                .allMatch(ctx -> ctx.getExpectedResponseCode() == com.endava.cats.http.ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(captor.getAllValues())
                .filteredOn(ctx -> ctx.getScenario().contains("field [price]"))
                .allMatch(ctx -> ctx.getExpectedResponseCode() == com.endava.cats.http.ResponseCodeFamilyPredefined.FOURXX_TWOXX);
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("SmallNegativeAndInt32BoundaryFieldsFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).containsExactly(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
