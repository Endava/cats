package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@QuarkusTest
class PathBodyIdMismatchFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private TestCaseListener testCaseListener;
    private PathBodyIdMismatchFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        fuzzer = new PathBodyIdMismatchFuzzer(simpleExecutor, testCaseListener);
    }

    @Test
    void shouldReportErrorWhenMismatchedBodyIdIsReflectedOnPut() {
        FuzzingData data = FuzzingData.builder()
                .path("/orders/{orderId}")
                .method(HttpMethod.PUT)
                .payload("{\"id\":42,\"status\":\"SHIPPED\"}")
                .allFieldsByHttpMethod(Set.of("id", "status"))
                .requestPropertyTypes(Map.of("id", new IntegerSchema(), "status", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        SimpleExecutorContext context = captor.getValue();
        Assertions.assertThat(context.getPayload()).contains("\"id\":987654");

        CatsResponse vulnerableResponse = CatsResponse.builder()
                .responseCode(200)
                .body("{\"id\":987654,\"status\":\"SHIPPED\"}")
                .build();
        context.getResponseProcessor().accept(vulnerableResponse, data);

        Mockito.verify(testCaseListener).reportResultError(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq(CatsResultFactory.Reason.PATH_BODY_ID_MISMATCH_ACCEPTED.value()),
                Mockito.contains("id=987654"));
    }

    @Test
    void shouldReportInfoWhenBodyIdIgnoredOrRejected() {
        FuzzingData data = FuzzingData.builder()
                .path("/users/{userId}")
                .method(HttpMethod.PATCH)
                .payload("{\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"name\":\"Alice\"}")
                .allFieldsByHttpMethod(Set.of("userId", "name"))
                .requestPropertyTypes(Map.of("userId", new UUIDSchema(), "name", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        SimpleExecutorContext context = captor.getValue();
        Assertions.assertThat(context.getPayload())
                .contains("\"userId\":\"11111111-1111-1111-1111-111111111111\"");

        CatsResponse safeResponse = CatsResponse.builder()
                .responseCode(200)
                .body("{\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"name\":\"Alice\"}")
                .build();
        context.getResponseProcessor().accept(safeResponse, data);

        CatsResponse rejectedResponse = CatsResponse.builder().responseCode(400).body("{}").build();
        context.getResponseProcessor().accept(rejectedResponse, data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void shouldSkipWhenPathHasNoMatchingBodyId() {
        FuzzingData data = FuzzingData.builder()
                .path("/orders/{orderId}")
                .method(HttpMethod.PUT)
                .payload("{\"status\":\"SHIPPED\"}")
                .allFieldsByHttpMethod(Set.of("status"))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("PathBodyIdMismatchFuzzer");
    }

    @ParameterizedTest
    @MethodSource("mismatchValues")
    void shouldChooseDistinctCanary(Object current, Schema<?> schema, Object expected) {
        Assertions.assertThat(PathBodyIdMismatchFuzzer.selectMismatchedId(current, schema)).isEqualTo(expected);
    }

    static Stream<Arguments> mismatchValues() {
        return Stream.of(
                Arguments.of(987654L, null, 987655L),
                Arguments.of("987654", null, 987655L),
                Arguments.of("42", null, 987654L),
                Arguments.of(null, new IntegerSchema(), 987654L),
                Arguments.of("x", new NumberSchema(), 987654L),
                Arguments.of(PathBodyIdMismatchFuzzer.UUID_CANARY_PRIMARY, null, PathBodyIdMismatchFuzzer.UUID_CANARY_FALLBACK),
                Arguments.of(null, new UUIDSchema(), PathBodyIdMismatchFuzzer.UUID_CANARY_PRIMARY),
                Arguments.of(null, null, PathBodyIdMismatchFuzzer.STRING_CANARY),
                Arguments.of(PathBodyIdMismatchFuzzer.STRING_CANARY, null, PathBodyIdMismatchFuzzer.STRING_CANARY + "-2"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "{\"other\":987654}", "{\"id\":42}"})
    void shouldNotReflectMissingIdentifiers(String body) {
        Assertions.assertThat(PathBodyIdMismatchFuzzer.isMismatchedIdReflected(body, "orderId", 987654)).isFalse();
    }

    @Test
    void shouldReportServerErrors() {
        FuzzingData data = FuzzingData.builder().path("/items/{id}").payload("{\"id\":1}")
                .allFieldsByHttpMethod(Set.of("id")).build();
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(500).body("{}").build(), data);
        Mockito.verify(testCaseListener).reportResultError(Mockito.any(), Mockito.eq(data), Mockito.eq(CatsResultFactory.Reason.SERVER_ERROR.value()), Mockito.contains("500"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/items", "/items/{slug}", "/items/{orderId}"})
    void shouldSkipMissingOrUnrelatedPathIdentifiers(String path) {
        FuzzingData data = FuzzingData.builder().path(path).payload("{\"name\":\"x\"}")
                .allFieldsByHttpMethod(Set.of("nested#id", "id", "name")).build();
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldSkipEmptyPayload() {
        fuzzer.fuzz(FuzzingData.builder().payload("").build());
        Mockito.verifyNoInteractions(simpleExecutor);
    }


    @Test
    void shouldRecognizeGenericIdReflectionCaseInsensitively() {
        Assertions.assertThat(PathBodyIdMismatchFuzzer.isMismatchedIdReflected("{\"ID\":\"CANARY\"}", "orderId", "canary")).isTrue();
    }

}
