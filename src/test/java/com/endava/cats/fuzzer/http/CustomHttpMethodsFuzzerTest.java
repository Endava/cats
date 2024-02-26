package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

@QuarkusTest
class CustomHttpMethodsFuzzerTest {
    @Inject
    HttpMethodFuzzerUtil httpMethodFuzzerUtil;
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private CustomHttpMethodsFuzzer customHttpMethodsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        customHttpMethodsFuzzer = new CustomHttpMethodsFuzzer(httpMethodFuzzerUtil);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        ReflectionTestUtils.setField(httpMethodFuzzerUtil, "simpleExecutor", simpleExecutor);
    }

    @Test
    void shouldCallServiceAndReportErrorWhenServiceRespondsWith200() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        customHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(12)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 200}));
    }

    @Test
    void shouldCallServiceAndReportInfoWhenServiceRespondsWith405() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        customHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(12)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405}));
    }

    @Test
    void shouldCallServiceAndReportWarnWhenServiceRespondsWith400() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(400).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        customHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(12)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 400}));
    }

    @Test
    void shouldRunOncePerPath() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).path("/test").requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        customHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(12)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405}));
        Mockito.clearInvocations(testCaseListener);
        customHttpMethodsFuzzer.fuzz(data);
        Mockito.verifyNoMoreInteractions(testCaseListener);
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(customHttpMethodsFuzzer).hasToString(customHttpMethodsFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldNotSkipForAnyHttpMethod() {
        Assertions.assertThat(customHttpMethodsFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(customHttpMethodsFuzzer.description()).isNotBlank();
    }
}
