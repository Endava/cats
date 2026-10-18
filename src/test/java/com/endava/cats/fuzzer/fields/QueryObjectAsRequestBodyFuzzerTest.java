package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

@QuarkusTest
class QueryObjectAsRequestBodyFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private ProcessingArguments processingArguments;
    private QueryObjectAsRequestBodyFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        fuzzer = new QueryObjectAsRequestBodyFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldSkipForBodyMethodsOrPrimitiveQueryParams() {
        FuzzingData postData = Mockito.mock(FuzzingData.class);
        Mockito.when(postData.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(postData.getPayload()).thenReturn("{\"a\":1}");
        fuzzer.fuzz(postData);
        Mockito.verifyNoInteractions(simpleExecutor);

        FuzzingData getPrimitive = Mockito.mock(FuzzingData.class);
        Mockito.when(getPrimitive.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(getPrimitive.getPayload()).thenReturn("{\"page\":1}");
        Mockito.when(getPrimitive.getQueryParams()).thenReturn(Set.of("page"));
        fuzzer.fuzz(getPrimitive);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldSendQueryObjectAsRequestBodyOnGet() {
        FuzzingData getWithObject = Mockito.mock(FuzzingData.class);
        Mockito.when(getWithObject.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(getWithObject.getPath()).thenReturn("/api/v1/airports");
        Mockito.when(getWithObject.getPayload())
                .thenReturn("{\"request\":{\"pagination\":{\"pageNumber\":1,\"pageSize\":5},\"sorting\":{\"sortBy\":\"name\"}}}");
        Mockito.when(getWithObject.getQueryParams()).thenReturn(Set.of("request"));
        Mockito.when(getWithObject.getAllRequiredFields())
                .thenReturn(List.of("request", "request#pagination", "request#pagination#pageNumber", "request#pagination#pageSize"));

        fuzzer.fuzz(getWithObject);

        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(Mockito.any());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("QueryObjectAsRequestBodyFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).contains(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
    }
}
