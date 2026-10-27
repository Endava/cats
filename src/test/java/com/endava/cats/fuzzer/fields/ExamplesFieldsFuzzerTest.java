package com.endava.cats.fuzzer.fields;


import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class ExamplesFieldsFuzzerTest {

    private ExamplesFieldsFuzzer examplesFieldsFuzzer;
    private SimpleExecutor simpleExecutor;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);

        examplesFieldsFuzzer = new ExamplesFieldsFuzzer(simpleExecutor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(examplesFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(examplesFieldsFuzzer).hasToString("ExamplesFieldsFuzzer");
    }

    @Test
    void shouldNotRunWhenNoExamples() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getReqSchema()).thenReturn(new Schema<String>());
        examplesFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldExecuteBasedOnExample() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExample("Example");
        Mockito.when(data.getReqSchema()).thenReturn(schema);

        examplesFieldsFuzzer.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldExecuteBasedOnExamples() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExamples(List.of("Example1", "Example2"));
        Mockito.when(data.getReqSchema()).thenReturn(schema);

        examplesFieldsFuzzer.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(2)).execute(Mockito.any());
    }

    @Test
    void shouldExecuteBasedOnPropertyExamplesInPayload() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getReqSchema()).thenReturn(new Schema<>());
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"name\":\"generated\",\"code\":\"gen\"}");

        Schema<String> nameSchema = new Schema<>();
        nameSchema.setExample("Alice");
        Schema<String> codeSchema = new Schema<>();
        codeSchema.setExamples(List.of("CODE_1"));
        DateSchema dateSchema = new DateSchema();
        dateSchema.setExample(Date.from(Instant.parse("2021-07-02T00:00:00Z")));
        DateTimeSchema dateTimeSchema = new DateTimeSchema();
        dateTimeSchema.setExample(OffsetDateTime.of(2022, 7, 2, 10, 30, 0, 0, ZoneOffset.UTC));
        Mockito.when(data.getPayload()).thenReturn("{\"name\":\"generated\",\"code\":\"gen\",\"startDate\":\"2026-01-01\",\"createdAt\":\"2026-01-01T00:00:00Z\"}");
        Map<String, Schema> propTypes = new HashMap<>();
        propTypes.put("name", nameSchema);
        propTypes.put("code", codeSchema);
        propTypes.put("startDate", dateSchema);
        propTypes.put("createdAt", dateTimeSchema);
        propTypes.put("nullSchema", null);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propTypes);

        examplesFieldsFuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPayload())
                .contains("\"startDate\":\"2021-07-02\"")
                .contains("\"createdAt\":\"2022-07-02T10:30:00Z\"");
        Assertions.assertThat(examplesFieldsFuzzer.skipForHttpMethods())
                .containsOnly(HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.DELETE);
    }
}
