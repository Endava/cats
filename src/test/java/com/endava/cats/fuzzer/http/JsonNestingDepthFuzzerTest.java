package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JsonNestingDepthFuzzerTest {
    private ServiceCaller caller;
    private TestCaseListener listener;
    private JsonNestingDepthFuzzer fuzzer;

    @BeforeEach
    void setup() {
        caller = Mockito.mock(ServiceCaller.class);
        listener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> { invocation.<Runnable>getArgument(2).run(); return null; })
                .when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(caller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        fuzzer = new JsonNestingDepthFuzzer(new SimpleExecutor(listener, caller), new RobustnessResponseReporter(listener));
    }

    @Test
    void shouldPreserveEveryRawDocumentAndOnlyRunOncePerOperation() {
        fuzzer.fuzz(data("application/json", HttpMethod.POST));
        fuzzer.fuzz(data("application/json", HttpMethod.POST));
        var requests = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(caller, Mockito.times(24)).call(requests.capture());
        assertThat(requests.getAllValues()).extracting(ServiceData::getPayload)
                .containsExactlyElementsOf(JsonNestingInputs.probes().stream().map(JsonNestingInputs.Probe::payload).toList());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.isValidJson()).isFalse();
            assertThat(request.isReplaceRefData()).isFalse();
            assertThat(request.getMutationTargets()).containsExactly(RequestTarget.requestBody());
        });
        fuzzer.fuzz(data("application/json", HttpMethod.PUT));
        Mockito.verify(caller, Mockito.times(48)).call(Mockito.any());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 302, 400, 401, 413, 422, 500, 502, 953})
    void shouldUseExistingReportingWithoutAssigningConfidence(int status) {
        var response = CatsResponse.builder().responseCode(status).body("{}").build();
        Mockito.when(caller.call(Mockito.any())).thenReturn(response);
        var data = data("application/json", HttpMethod.POST);
        fuzzer.fuzz(data);
        if (status >= 200 && status < 500) {
            Mockito.verify(listener, Mockito.times(24)).reportResultInfo(Mockito.any(), Mockito.same(data), Mockito.anyString(), Mockito.eq(status));
        } else {
            Mockito.verify(listener, Mockito.times(24)).reportResult(Mockito.any(), Mockito.same(data), Mockito.same(response),
                    Mockito.eq(new ResponseCodeFamilyDynamic(List.of("2XX", "3XX", "4XX"))), Mockito.eq(false), Mockito.eq(false));
        }
        Mockito.verify(listener, Mockito.never()).addConfidence(Mockito.any());
    }

    @Test
    void shouldSkipOtherFormatsBodylessOperationsAndFilteredExecution() {
        fuzzer.fuzz(data("text/plain", HttpMethod.POST));
        fuzzer.fuzz(data("application/json", HttpMethod.GET));
        Mockito.verifyNoInteractions(caller);
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(false);
        fuzzer.fuzz(data("application/json", HttpMethod.POST));
        Mockito.verifyNoInteractions(caller);
        assertThat(fuzzer.description()).contains("1024");
    }

    private static FuzzingData data(String type, HttpMethod method) {
        return FuzzingData.builder().path("/nested").method(method).payload("{}").headers(Set.of()).requestContentTypes(List.of(type)).build();
    }
}
