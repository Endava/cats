package com.endava.cats.fuzzer.executor;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@QuarkusTest
class SimpleExecutorTest {

    @Test
    void shouldRetainTheOriginalPayloadForAutomaticMutationDetection() {
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        Fuzzer fuzzer = Mockito.mock(Fuzzer.class);
        FuzzingData fuzzingData = Mockito.mock(FuzzingData.class);
        Mockito.when(fuzzingData.getPath()).thenReturn("/customers/{customerId}");
        Mockito.when(fuzzingData.getContractPath()).thenReturn("/customers/{customerId}");
        Mockito.when(fuzzingData.getPayload()).thenReturn("{\"customerId\":\"original\"}");
        Mockito.when(fuzzingData.getHeaders()).thenReturn(Set.of());
        Mockito.when(fuzzingData.getQueryParams()).thenReturn(Set.of());
        Mockito.when(fuzzingData.getQueryParameterSerializations()).thenReturn(Map.of());
        Mockito.when(fuzzingData.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(fuzzingData.getFirstRequestContentType()).thenReturn("application/json");
        Mockito.when(testCaseListener.shouldContinueExecution(logger, ResponseCodeFamilyPredefined.FOURXX))
                .thenReturn(true);
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.eq(logger), Mockito.eq(fuzzer),
                Mockito.any(Runnable.class), Mockito.eq(fuzzingData));
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());

        new SimpleExecutor(testCaseListener, serviceCaller).execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzer(fuzzer)
                .fuzzingData(fuzzingData)
                .payload("{\"customerId\":\"mutated\"}")
                .mutationTarget(RequestTarget.body("customerId"))
                .mutationTarget(RequestTarget.header("X-Test"))
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .build());

        ArgumentCaptor<ServiceData> serviceData = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(serviceData.capture());
        Assertions.assertThat(serviceData.getValue().getPayload()).contains("mutated");
        Assertions.assertThat(serviceData.getValue().getOriginalPayload()).contains("original");
        Assertions.assertThat(serviceData.getValue().getAllMutationTargets()).containsExactly(
                RequestTarget.body("customerId"), RequestTarget.header("X-Test"));
    }

    @Test
    void shouldReplayRequestWithinCurrentTestCaseWithoutReplacingItsExchange() {
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        FuzzingData fuzzingData = Mockito.mock(FuzzingData.class);
        Mockito.when(fuzzingData.getPath()).thenReturn("/customers");
        Mockito.when(fuzzingData.getHeaders()).thenReturn(Set.of());
        Mockito.when(fuzzingData.getQueryParams()).thenReturn(Set.of());
        Mockito.when(fuzzingData.getQueryParameterSerializations()).thenReturn(Map.of());
        Mockito.when(fuzzingData.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(fuzzingData.getFirstRequestContentType()).thenReturn("application/json");
        Mockito.when(testCaseListener.callPreservingCurrentExchange(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());
        CatsResponse replayed = CatsResponse.builder().responseCode(201).body("{}").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(replayed);

        CatsResponse response = new SimpleExecutor(testCaseListener, serviceCaller).replay(SimpleExecutorContext.builder()
                .fuzzingData(fuzzingData)
                .payload("{\"name\":\"' OR '1'='1\"}")
                .mutationTarget(RequestTarget.body("name"))
                .build());

        Assertions.assertThat(response).isSameAs(replayed);
        ArgumentCaptor<ServiceData> serviceData = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(serviceData.capture());
        Assertions.assertThat(serviceData.getValue().getPayload()).contains("' OR '1'='1");
        Mockito.verify(testCaseListener).callPreservingCurrentExchange(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.never()).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReplayTheUnfuzzedRequestWithinCurrentTestCase() {
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        FuzzingData fuzzingData = Mockito.mock(FuzzingData.class);
        Mockito.when(fuzzingData.getPath()).thenReturn("/customers");
        Mockito.when(fuzzingData.getPayload()).thenReturn("{\"name\":\"original\"}");
        Mockito.when(fuzzingData.getHeaders()).thenReturn(Set.of());
        Mockito.when(fuzzingData.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(testCaseListener.callPreservingCurrentExchange(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());

        new SimpleExecutor(testCaseListener, serviceCaller).replayOriginal(fuzzingData);

        ArgumentCaptor<ServiceData> serviceData = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(serviceData.capture());
        Assertions.assertThat(serviceData.getValue().getPayload()).isEqualTo("{\"name\":\"original\"}");
        Assertions.assertThat(serviceData.getValue().getMutationTargets()).isEmpty();
        Mockito.verify(testCaseListener).callPreservingCurrentExchange(Mockito.any());
    }
}
