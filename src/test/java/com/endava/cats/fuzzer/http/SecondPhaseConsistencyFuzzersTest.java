package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.RuntimeResourcePool;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@QuarkusTest
class SecondPhaseConsistencyFuzzersTest {
    private RuntimeResourcePool pool;
    private FilterArguments filters;
    private ApiArguments arguments;
    private SimpleExecutor executor;
    private TestCaseListener listener;

    @BeforeEach
    void setup() {
        pool = Mockito.mock(RuntimeResourcePool.class);
        filters = Mockito.mock(FilterArguments.class);
        Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(true);
        arguments = new ApiArguments();
        arguments.setServer("https://api.example.com");
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
    }

    @Test
    void conditionalGetSendsObservedTagOnlyToOriginalSameOriginPath() {
        FuzzingData data = getData();
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange(data.getPath(), HttpMethod.GET,
                        "https://api.example.com/items/1", "{}", 200, "{}", null, "\"abc\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPath()).isEqualTo("/items/1");
        Assertions.assertThat(captor.getValue().isReplaceRefData()).isFalse();
        Assertions.assertThat(captor.getValue().getHeaders()).anySatisfy(header -> {
            Assertions.assertThat(header.getName()).isEqualTo("If-None-Match");
            Assertions.assertThat(header.getValue()).isEqualTo("\"abc\"");
        });
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .headers(List.of(new KeyValuePair<>("ETag", "\"abc\""))).build(), data);
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data), Mockito.eq("Conditional GET ignored"), Mockito.anyString());
    }

    @Test
    void staleIfMatchOnlyReplaysWritesToOwnedResourcesWithTwoDistinctObservedTags() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                get("\"old\""), get("\"new\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getMutationTargets()).hasSize(1);
        Assertions.assertThat(captor.getValue().getHeaders()).anySatisfy(header -> {
            Assertions.assertThat(header.getName()).isEqualTo("If-Match");
            Assertions.assertThat(header.getValue()).isEqualTo("\"old\"");
        });
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(412).build(), data);
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.<Object[]>any());
    }

    @Test
    void staleIfMatchSkipsWhenNoOwnedItemExists() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                get("\"old\""), get("\"new\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(data);
        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void readBackSkipsUnownedResourcesAndDoesNotSendAnotherUpdate() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        UpdateReadConsistencyFuzzer fuzzer = new UpdateReadConsistencyFuzzer(pool, arguments, filters, executor, listener);
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(executor);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getHttpMethod()).isEqualTo(HttpMethod.GET);
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .body("{\"name\":\"changed\",\"secret\":\"value\"}").build(), data);
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data), Mockito.eq("Possible update/read inconsistency"),
                Mockito.anyString(), Mockito.<Object[]>any());
    }

    @Test
    void putIdempotencyRunsTwoIdenticalWritesAndReadsOnlyOnOwnedResource() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        PutIdempotencyFuzzer fuzzer = new PutIdempotencyFuzzer(pool, arguments, filters, executor, listener);
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(executor);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        List<SimpleExecutorContext> sent = new ArrayList<>();
        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            sent.add(step);
            CatsResponse response = step.getHttpMethod() == HttpMethod.PUT
                    ? CatsResponse.builder().responseCode(200).body("{}").build()
                    : CatsResponse.builder().responseCode(200).body("{\"name\":\"same\",\"version\":1}").build();
            step.getResponseProcessor().accept(response, data);
            return null;
        }).when(executor).execute(Mockito.any());
        fuzzer.fuzz(data);
        Assertions.assertThat(sent).extracting(SimpleExecutorContext::getHttpMethod)
                .containsExactly(HttpMethod.PUT, HttpMethod.GET, HttpMethod.PUT, HttpMethod.GET);
        Assertions.assertThat(sent.get(0).getPayload()).isEqualTo(sent.get(2).getPayload());
        Assertions.assertThat(sent.get(0).getPath()).isEqualTo("/items/1");
        Assertions.assertThat(sent.get(0).isReplaceRefData()).isFalse();
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data),
                Mockito.eq("Resource state did not change after repeating the identical PUT"));
    }

    @Test
    void putIdempotencyWarnsWhenSecondPutChangesState() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        int[] reads = {0};
        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            CatsResponse response = step.getHttpMethod() == HttpMethod.PUT
                    ? CatsResponse.builder().responseCode(200).body("{}").build()
                    : CatsResponse.builder().responseCode(200).body("{\"name\":\"" + (++reads[0]) + "\"}").build();
            step.getResponseProcessor().accept(response, data);
            return null;
        }).when(executor).execute(Mockito.any());
        new PutIdempotencyFuzzer(pool, arguments, filters, executor, listener).fuzz(data);
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data),
                Mockito.eq("PUT is not idempotent"), Mockito.anyString());
    }

    private FuzzingData getData() {
        return FuzzingData.builder().method(HttpMethod.GET).path("/items/{id}").contractPath("/items/{id}")
                .headers(Set.of()).payload("{}").build();
    }

    private FuzzingData writeData(HttpMethod method) {
        return FuzzingData.builder().method(method).path("/items/{id}").contractPath("/items/{id}")
                .pathItem(new PathItem().get(new Operation()).put(new Operation())).headers(Set.of())
                .reqSchema(new ObjectSchema().addProperty("name", new StringSchema())
                        .addProperty("secret", new StringSchema().writeOnly(true)))
                .payload("{\"name\":\"same\"}").build();
    }

    private RuntimeResourcePool.SuccessfulExchange put() {
        return new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                "https://api.example.com/items/1", "{\"name\":\"same\"}", 200, "{}", null, null);
    }

    private RuntimeResourcePool.SuccessfulExchange get(String etag) {
        return new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.GET,
                "https://api.example.com/items/1", "{}", 200, "{}", null, etag);
    }

    private RuntimeResourcePool.SuccessfulExchange post() {
        return new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST,
                "https://api.example.com/items", "{}", 201, "{}", "/items/1", null);
    }
}
