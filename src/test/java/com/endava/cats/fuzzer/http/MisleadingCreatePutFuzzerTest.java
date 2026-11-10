package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@QuarkusTest
class MisleadingCreatePutFuzzerTest {
    private RuntimeResourcePool pool;
    private FilterArguments filters;
    private ApiArguments arguments;
    private SimpleExecutor executor;
    private TestCaseListener listener;
    private MisleadingCreatePutFuzzer fuzzer;

    @BeforeEach
    void setup() {
        pool = Mockito.mock(RuntimeResourcePool.class);
        filters = Mockito.mock(FilterArguments.class);
        Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(true);
        arguments = new ApiArguments();
        arguments.setServer("https://api.example.com");
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
        fuzzer = new MisleadingCreatePutFuzzer(pool, arguments, filters, executor, listener);
    }

    @Test
    void shouldReportErrorWhenPutAfterGetReturns201OnOwnedResource() {
        ownedPut(200);
        FuzzingData data = putData();
        List<SimpleExecutorContext> sent = executeSequence(data, 200, 201);

        Assertions.assertThat(sent).extracting(SimpleExecutorContext::getHttpMethod)
                .containsExactly(HttpMethod.GET, HttpMethod.PUT);
        Assertions.assertThat(sent.getFirst().getPath()).isEqualTo("/items/1");
        Assertions.assertThat(sent.getLast().getPath()).isEqualTo("/items/1");
        Assertions.assertThat(sent.getLast().getPayload()).isEqualTo("{\"name\":\"Updated\"}");
        Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data),
                Mockito.eq(Reason.MISLEADING_CREATE_PUT.value()), Mockito.anyString());
    }

    @Test
    void shouldReportErrorWhenPutCreatedResourceWithoutPostAndSubsequentPutAfterGetReturns201() {
        Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PUT)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                        "https://api.example.com/items/42", "{\"name\":\"CreatedByPut\"}", 201, "{}", null, null)));
        FuzzingData data = putData();
        List<SimpleExecutorContext> sent = executeSequence(data, 200, 201);

        Assertions.assertThat(sent).extracting(SimpleExecutorContext::getPath).containsExactly("/items/42", "/items/42");
        Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data),
                Mockito.eq(Reason.MISLEADING_CREATE_PUT.value()), Mockito.anyString());
    }

    @ParameterizedTest
    @CsvSource({
            "0,500,200,error,1",
            "0,404,201,info,1",
            "1,200,500,error,2",
            "1,200,200,info,2",
            "1,200,204,info,2",
            "1,200,400,info,2"
    })
    void shouldClassifyEveryStage(int stage, int getStatus, int putStatus, String verdict, int expectedCalls) {
        ownedPut(200);
        FuzzingData data = putData();
        List<SimpleExecutorContext> sent = executeSequence(data, getStatus, putStatus);

        Assertions.assertThat(sent).hasSize(expectedCalls);
        if (verdict.equals("error")) {
            Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data),
                    Mockito.eq(Reason.SERVER_ERROR.value()), Mockito.anyString(), Mockito.any());
        } else {
            Mockito.verify(listener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.any());
            Mockito.verify(listener, Mockito.never()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"method", "pathItem", "get", "filter", "ifMatch", "ifNoneMatch", "unowned200"})
    void shouldSkipWhenOutOfScope(String reason) {
        FuzzingData data = putData();
        switch (reason) {
            case "method" -> data = data.toBuilder().method(HttpMethod.POST).build();
            case "pathItem" -> data = data.toBuilder().pathItem(null).build();
            case "get" -> data = data.toBuilder().pathItem(new PathItem().put(new Operation())).build();
            case "filter" -> Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(false);
            case "ifMatch" -> data = data.toBuilder().headers(Set.of(CatsHeader.from("If-Match", "\"1\"", false, null))).build();
            case "ifNoneMatch" -> data = data.toBuilder().headers(Set.of(CatsHeader.from("If-None-Match", "*", false, null))).build();
            default -> Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PUT)).thenReturn(List.of(
                    new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                            "https://api.example.com/items/1", "{\"name\":\"Updated\"}", 200, "{}", null, null)));
        }
        if (!reason.equals("unowned200")) {
            ownedPut(200);
        }

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("MisleadingCreatePutFuzzer");
    }

    private List<SimpleExecutorContext> executeSequence(FuzzingData data, int getStatus, int putStatus) {
        List<SimpleExecutorContext> sent = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext ctx = invocation.getArgument(0);
            sent.add(ctx);
            int status = ctx.getHttpMethod() == HttpMethod.GET ? getStatus : putStatus;
            ctx.getResponseProcessor().accept(CatsResponse.builder().responseCode(status).body("{}").build(), data);
            return null;
        }).when(executor).execute(Mockito.any());
        fuzzer.fuzz(data);
        return sent;
    }

    private void ownedPut(int putStatus) {
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST,
                        "https://api.example.com/items", "{}", 201, "{}", "https://api.example.com/items/1", null)));
        Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PUT)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                        "https://api.example.com/items/1", "{\"name\":\"Updated\"}", putStatus, "{}", null, null)));
    }

    private static FuzzingData putData() {
        return FuzzingData.builder()
                .method(HttpMethod.PUT)
                .path("/items/{id}")
                .contractPath("/items/{id}")
                .pathItem(new PathItem().get(new Operation()).put(new Operation()))
                .headers(Set.of())
                .build();
    }
}
