package com.endava.cats.fuzzer.fields;


import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

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
        Mockito.when(data.getMethod()).thenReturn(com.endava.cats.http.HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"name\":\"generated\",\"code\":\"gen\"}");

        Schema<String> nameSchema = new Schema<>();
        nameSchema.setExample("Alice");
        Schema<String> codeSchema = new Schema<>();
        codeSchema.setExamples(List.of("CODE_1"));
        java.util.Map<String, Schema> propTypes = new java.util.HashMap<>();
        propTypes.put("name", nameSchema);
        propTypes.put("code", codeSchema);
        propTypes.put("nullSchema", null);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propTypes);

        examplesFieldsFuzzer.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(Mockito.any());
        Assertions.assertThat(examplesFieldsFuzzer.skipForHttpMethods())
                .containsOnly(com.endava.cats.http.HttpMethod.HEAD, com.endava.cats.http.HttpMethod.TRACE, com.endava.cats.http.HttpMethod.DELETE);
    }
}
