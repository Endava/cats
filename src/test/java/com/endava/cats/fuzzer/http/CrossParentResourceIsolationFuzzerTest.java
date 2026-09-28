package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.RuntimeResourcePool;
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

    private RuntimeResourcePool.SuccessfulExchange exchange(String url) {
        return new RuntimeResourcePool.SuccessfulExchange(data.getPath(), HttpMethod.GET, url, "{}", 200, "{}", null, null);
    }
}
