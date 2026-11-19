package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.HttpHeaders;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

@QuarkusTest
class ContentTypeParameterDifferentialHeadersFuzzerTest {
    private ServiceCaller caller;
    private TestCaseListener listener;
    private ContentTypeParameterDifferentialHeadersFuzzer fuzzer;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        caller = Mockito.mock(ServiceCaller.class);
        listener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(caller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(415).body("{}").build());
        fuzzer = new ContentTypeParameterDifferentialHeadersFuzzer(new SimpleExecutor(listener, caller), new RobustnessResponseReporter(listener));
    }

    @Test
    void shouldHaveDescriptionAndSkipBodylessMethods() {
        Assertions.assertThat(fuzzer.description()).contains("Content-Type", "charset", "boundary");
        Assertions.assertThat(fuzzer).hasToString("ContentTypeParameterDifferentialHeadersFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @ParameterizedTest
    @CsvSource({
            "'application/json; charset=utf-8', application/json",
            "'multipart/form-data; boundary=abc', multipart/form-data",
            "'', application/json",
            "'; charset=utf-8', application/json"
    })
    void shouldExtractBaseMediaType(String raw, String expected) {
        Assertions.assertThat(ContentTypeParameterDifferentialHeadersFuzzer.extractBaseMediaType(raw)).isEqualTo(expected);
    }

    @Test
    void shouldSendFiveParameterDifferentialVariantsAndPreserveOtherHeaders() {
        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.POST)
                .payload("{\"name\":\"item\"}")
                .requestContentTypes(List.of("application/json; charset=utf-8"))
                .headers(Set.of(
                        CatsHeader.from("X-Trace", "keep", false, null),
                        CatsHeader.from("Content-Type", "application/json", false, null)))
                .build();

        fuzzer.fuzz(data);
        fuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(caller, Mockito.times(5)).call(captor.capture());
        Assertions.assertThat(captor.getAllValues()).allSatisfy(serviceData -> {
            Assertions.assertThat(serviceData.getMutationTargets()).containsExactly(RequestTarget.header(HttpHeaders.CONTENT_TYPE));
            Assertions.assertThat(serviceData.getHeaders()).anyMatch(h -> "X-Trace".equals(h.getName()) && "keep".equals(h.getValue()));
            Assertions.assertThat(serviceData.getHeaders())
                    .filteredOn(h -> HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(h.getName()))
                    .hasSize(1)
                    .first()
                    .extracting(CatsHeader::getValue)
                    .asString()
                    .startsWith("application/json; ");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 415, 500})
    void shouldReportControlledResponsesAsInfoAndServerErrorsAsFailures(int status) {
        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.POST)
                .payload("{\"name\":\"item\"}")
                .requestContentTypes(List.of("application/json"))
                .build();
        CatsResponse response = CatsResponse.builder().responseCode(status).body("{}").build();
        Mockito.when(caller.call(Mockito.any())).thenReturn(response);

        fuzzer.fuzz(data);

        if (status < 500) {
            Mockito.verify(listener, Mockito.times(5)).reportResultInfo(
                    Mockito.any(), Mockito.same(data), Mockito.anyString(), Mockito.eq(status));
        } else {
            Mockito.verify(listener, Mockito.times(5)).reportResult(
                    Mockito.any(), Mockito.same(data), Mockito.same(response),
                    Mockito.eq(new ResponseCodeFamilyDynamic(List.of("2XX", "3XX", "4XX"))),
                    Mockito.eq(false), Mockito.eq(false));
        }
    }

    @Test
    void shouldSkipBodylessOrEmptyPayloadRequests() {
        FuzzingData getData = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.GET)
                .payload("{\"id\":1}")
                .requestContentTypes(List.of("application/json"))
                .build();
        FuzzingData emptyBody = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.POST)
                .payload("")
                .requestContentTypes(List.of("application/json"))
                .build();

        fuzzer.fuzz(null);
        fuzzer.fuzz(getData);
        fuzzer.fuzz(emptyBody);

        Mockito.verifyNoInteractions(caller);
    }
}
