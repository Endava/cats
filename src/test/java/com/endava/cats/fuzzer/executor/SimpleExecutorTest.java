package com.endava.cats.fuzzer.executor;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

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
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .build());

        ArgumentCaptor<ServiceData> serviceData = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(serviceData.capture());
        Assertions.assertThat(serviceData.getValue().getPayload()).contains("mutated");
        Assertions.assertThat(serviceData.getValue().getOriginalPayload()).contains("original");
    }
}
