package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.RuntimeResourcePool;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

@QuarkusTest
class CheckReadsStillWorkFuzzerTest {
    private ServiceCaller serviceCaller;
    private RuntimeResourcePool runtimeResourcePool;
    @InjectSpy
    TestCaseListener testCaseListener;
    private CheckReadsStillWorkFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        runtimeResourcePool = Mockito.mock(RuntimeResourcePool.class);
        fuzzer = new CheckReadsStillWorkFuzzer(new SimpleExecutor(testCaseListener, serviceCaller), testCaseListener, runtimeResourcePool);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        Mockito.doNothing().when(testCaseListener).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldHaveDescriptionToStringAndOnlyRunForGet() {
        Assertions.assertThat(fuzzer.description()).contains("repeat GET requests");
        Assertions.assertThat(fuzzer).hasToString("CheckReadsStillWorkFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).doesNotContain(HttpMethod.GET)
                .contains(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Test
    void shouldBeSecondPhaseFuzzerTriggeredOnlyByPathCompletion() {
        SecondPhaseFuzzer annotation = CheckReadsStillWorkFuzzer.class.getAnnotation(SecondPhaseFuzzer.class);

        Assertions.assertThat(annotation).isNotNull();
        Assertions.assertThat(annotation.triggers()).containsExactly(SecondPhaseFuzzer.Trigger.PATH_COMPLETED);
    }

    @Test
    void shouldRepeatGetWhenPathHasWritesAndGetSucceededBefore() {
        FuzzingData data = getData(new PathItem().get(new Operation()).post(new Operation()));
        Mockito.when(runtimeResourcePool.hasSucceededWithUnmodifiedRequest("/users", HttpMethod.GET)).thenReturn(true);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response(200));

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller).call(Mockito.argThat(serviceData -> serviceData.getHttpMethod() == HttpMethod.GET &&
                "/users".equals(serviceData.getRelativePath()) && !serviceData.hasDeclaredMutation()));
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX));
    }

    @Test
    void shouldSkipWhenGetDidNotSucceedBefore() {
        FuzzingData data = getData(new PathItem().get(new Operation()).put(new Operation()));
        Mockito.when(runtimeResourcePool.hasSucceededWithUnmodifiedRequest("/users", HttpMethod.GET)).thenReturn(false);

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldSkipWhenPathHasNoWriteOperations() {
        FuzzingData data = getData(new PathItem().get(new Operation()).delete(new Operation()));
        Mockito.when(runtimeResourcePool.hasSucceededWithUnmodifiedRequest("/users", HttpMethod.GET)).thenReturn(true);

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void shouldReportErrorWhenReadReturnsServerError(int code) {
        FuzzingData data = getData(new PathItem().get(new Operation()).patch(new Operation()));

        fuzzer.checkResponse(response(code), data);

        Mockito.verify(testCaseListener).reportResultError(Mockito.any(), Mockito.eq(data),
                Mockito.eq(CatsResultFactory.Reason.READ_BROKEN_AFTER_FUZZING.value()), Mockito.anyString(),
                AdditionalMatchers.aryEq(new Object[]{String.valueOf(code)}));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void shouldReportInfoWhenResourceWasDeleted(int code) {
        FuzzingData data = getData(new PathItem().get(new Operation()).post(new Operation()));

        fuzzer.checkResponse(response(code), data);

        Mockito.verify(testCaseListener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(),
                AdditionalMatchers.aryEq(new Object[]{String.valueOf(code)}));
        Mockito.verify(testCaseListener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 401, 953})
    void shouldUseStandardReportingForOtherResponses(int code) {
        FuzzingData data = getData(new PathItem().get(new Operation()).post(new Operation()));

        fuzzer.checkResponse(response(code), data);

        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX));
        Mockito.verify(testCaseListener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    private static FuzzingData getData(PathItem pathItem) {
        return FuzzingData.builder().method(HttpMethod.GET).path("/users").contractPath("/users").payload("{}")
                .headers(Set.of()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json"))
                .pathItem(pathItem).build();
    }

    private static CatsResponse response(int code) {
        return CatsResponse.builder().responseCode(code).body("{}").responseContentType("application/json").build();
    }
}
