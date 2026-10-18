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
        Mockito.when(data.getPayload()).thenReturn("{\"page\":1}");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(data.getPath()).thenReturn("/items");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("page"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("page", new IntegerSchema()));

        fuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(Mockito.any());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("SmallNegativeAndInt32BoundaryFieldsFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).containsExactly(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
