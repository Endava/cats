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
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

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
}
