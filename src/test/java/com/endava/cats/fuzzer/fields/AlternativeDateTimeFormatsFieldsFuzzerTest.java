package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class AlternativeDateTimeFormatsFieldsFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private ProcessingArguments processingArguments;
    private AlternativeDateTimeFormatsFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        fuzzer = new AlternativeDateTimeFormatsFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldSkipWhenEmptyPayloadOrNoDateTimeFields() {
        FuzzingData emptyData = Mockito.mock(FuzzingData.class);
        Mockito.when(emptyData.getPayload()).thenReturn("{}");
        fuzzer.fuzz(emptyData);
        Mockito.verifyNoInteractions(simpleExecutor);

        FuzzingData noDateData = Mockito.mock(FuzzingData.class);
        Mockito.when(noDateData.getPayload()).thenReturn("{\"name\":\"test\"}");
        Mockito.when(noDateData.getAllFieldsByHttpMethod()).thenReturn(Set.of("name"));
        Mockito.when(noDateData.getRequestPropertyTypes()).thenReturn(Map.of());
        fuzzer.fuzz(noDateData);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldFuzzDateTimeFieldsWithAlternativeFormats() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(true);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"departureTime\":\"2026-10-16T10:00:00Z\"}");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPath()).thenReturn("/search");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("departureTime"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("departureTime", new DateTimeSchema()));

        fuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(Mockito.any());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("AlternativeDateTimeFormatsFieldsFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).containsExactly(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
