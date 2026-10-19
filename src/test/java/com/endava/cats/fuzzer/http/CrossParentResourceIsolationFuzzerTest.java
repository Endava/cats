package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

@QuarkusTest
class CrossParentResourceIsolationFuzzerTest {
    private SimpleExecutor executor;
    private RuntimeResourcePool pool;
    private TestCaseListener listener;
    private CrossParentResourceIsolationFuzzer fuzzer;
    private FuzzingData data;

    @BeforeEach
    void setup() {
        executor = Mockito.mock(SimpleExecutor.class);
        pool = Mockito.mock(RuntimeResourcePool.class);
        listener = Mockito.mock(TestCaseListener.class);
        ApiArguments args = new ApiArguments();
        args.setServer("https://api.example.com/v1");
        fuzzer = new CrossParentResourceIsolationFuzzer(pool, args, executor, listener);
        data = FuzzingData.builder().method(HttpMethod.GET).path("/users/{userId}/orders/{orderId}")
                .contractPath("/users/{userId}/orders/{orderId}").build();
    }

    @Test
    void swapsOnlyObservedParentAndReportsConfirmedExposureAsWarning() {
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                exchange("https://api.example.com/v1/users/a/orders/order-1"),
                exchange("https://api.example.com/v1/users/b/orders/order-2")));
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPath()).isEqualTo("/users/b/orders/order-1");
        Assertions.assertThat(captor.getValue().isReplaceRefData()).isFalse();
        Assertions.assertThat(captor.getValue().getMutationTargets()).hasSize(1);
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .body("{\"id\":\"order-1\"}").build(), data);
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data),
                Mockito.eq("Potential cross-parent resource exposure"), Mockito.anyString());
    }

    @Test
    void usesAlternativeParentFromResourcePoolWhenOnlyOneNestedGetExchangeWasObserved() {
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                exchange("https://api.example.com/v1/users/a/orders/order-1")));
        Mockito.when(pool.alternativePathParameterValue(data.getPath(), "userId", "a"))
                .thenReturn(Optional.of("b"));

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPath()).isEqualTo("/users/b/orders/order-1");
    }

    @Test
    void skipsSingleParentAndExternalUrls() {
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                exchange("https://api.example.com/v1/users/a/orders/order-1"),
                exchange("https://attacker.example/v1/users/b/orders/order-2")));
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void onlyMarksConfirmedIdentityAsWarning() {
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                exchange("https://api.example.com/v1/users/a/orders/order-1"),
                exchange("https://api.example.com/v1/users/b/orders/order-2")));
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .body("{\"id\":\"order-2\"}").build(), data);
        Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(404).build(), data);
        Mockito.verify(listener, Mockito.times(2)).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.<Object[]>any());
    }

    @Test
    void coversNonGetInvalidHierarchyThreeVariablesAndArrayAndErrorResponses() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("CrossParentResourceIsolationFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);

        fuzzer.fuzz(FuzzingData.builder().method(HttpMethod.POST).path("/users/{userId}/orders/{orderId}").build());
        fuzzer.fuzz(FuzzingData.builder().method(HttpMethod.GET).path("/users/{userId}").build());
        fuzzer.fuzz(FuzzingData.builder().method(HttpMethod.GET).path("/users/{userId}/orders/{orderId}/items").build());
        Mockito.verifyNoInteractions(executor);

        FuzzingData threeVarData = FuzzingData.builder().method(HttpMethod.GET)
                .path("/orgs/{orgId}/users/{userId}/orders/{orderId}")
                .contractPath("/orgs/{orgId}/users/{userId}/orders/{orderId}").build();
        Mockito.when(pool.successfulExchanges(threeVarData.getPath(), HttpMethod.GET)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange(threeVarData.getPath(), HttpMethod.GET,
                        "https://api.example.com/v1/orgs/o1/users/u1/orders/ord1", "{}", 200, "{}", null, null),
                new RuntimeResourcePool.SuccessfulExchange(threeVarData.getPath(), HttpMethod.GET,
                        "https://api.example.com/v1/orgs/o2/users/u2/orders/ord2", "{}", 200, "{}", null, null),
                new RuntimeResourcePool.SuccessfulExchange(threeVarData.getPath(), HttpMethod.GET,
                        "https://api.example.com/v1/orgs/o1/users/u2/orders/ord2", "{}", 200, "{}", null, null)));
        fuzzer.fuzz(threeVarData);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPath()).isEqualTo("/orgs/o1/users/u2/orders/ord1");

        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(403).build(), threeVarData);
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(500).build(), threeVarData);
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .body("[null, 42, {\"orderId\":\"ord1\"}]").build(), threeVarData);
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .body("not-json").build(), threeVarData);
        Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(threeVarData),
                Mockito.eq("Cross-parent request caused server error"), Mockito.anyString(), Mockito.<Object[]>any());
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(threeVarData),
                Mockito.eq("Potential cross-parent resource exposure"), Mockito.anyString());
    }

    private RuntimeResourcePool.SuccessfulExchange exchange(String url) {
        return new RuntimeResourcePool.SuccessfulExchange(data.getPath(), HttpMethod.GET, url, "{}", 200, "{}", null, null);
    }
}
