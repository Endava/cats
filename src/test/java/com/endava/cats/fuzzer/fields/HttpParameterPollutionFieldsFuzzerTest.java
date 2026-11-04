package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class HttpParameterPollutionFieldsFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private HttpParameterPollutionFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        fuzzer = new HttpParameterPollutionFieldsFuzzer(simpleExecutor);
    }

    @Test
    void shouldHaveDescriptionToStringAndSkippedMethods() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("HttpParameterPollutionFieldsFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).containsExactly(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldSkipWhenNoQueryParameters() {
        FuzzingData data = FuzzingData.builder().path("/items").method(HttpMethod.GET).queryParams(Set.of()).build();
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldDuplicateEachQueryParamWithConflictingValue() {
        Map<String, Schema> schemas = Map.of(
                "limit", new IntegerSchema(),
                "active", new BooleanSchema(),
                "status", new StringSchema());
        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.GET)
                .payload("{\"limit\":10,\"active\":true,\"status\":\"OPEN\"}")
                .queryParams(Set.of("limit", "active", "status"))
                .requestPropertyTypes(schemas)
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(captor.capture());

        Assertions.assertThat(captor.getAllValues())
                .extracting(SimpleExecutorContext::getPath)
                .anySatisfy(path -> Assertions.assertThat(path).contains("active=true&active=false"))
                .anySatisfy(path -> Assertions.assertThat(path).contains("limit=10&limit=-999999"))
                .anySatisfy(path -> Assertions.assertThat(path).contains("status=OPEN&status=catsPolluted"));
        Assertions.assertThat(captor.getAllValues().getFirst().getMutationTargets())
                .containsExactly(RequestTarget.query("active"));
    }
}
