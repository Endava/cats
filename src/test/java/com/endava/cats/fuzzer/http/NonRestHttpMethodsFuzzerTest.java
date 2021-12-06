package com.endava.cats.fuzzer.http;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.PathItem;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;

@QuarkusTest
class NonRestHttpMethodsFuzzerTest {
    @Inject
    HttpMethodFuzzerUtil httpMethodFuzzerUtil;
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private NonRestHttpMethodsFuzzer nonRestHttpMethodsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        nonRestHttpMethodsFuzzer = new NonRestHttpMethodsFuzzer(serviceCaller, testCaseListener, httpMethodFuzzerUtil);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldCallServiceAndReportErrorWhenServiceRespondsWith200() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(17)).reportError(Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 200}));
    }

    @Test
    void shouldCallServiceAndReportInfoWhenServiceRespondsWith405() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(17)).reportInfo(Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405}));
    }

    @Test
    void shouldCallServiceAndReportWarnWhenServiceRespondsWith400() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(400).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(17)).reportWarn(Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 400}));
    }

    @Test
    void shouldRunOncePerPath() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).path("/test").build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(17)).reportInfo(Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405}));
        Mockito.clearInvocations(testCaseListener);
        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verifyNoMoreInteractions(testCaseListener);
    }

    @Test
    void shouldOverrideMethods() {
        Assertions.assertThat(nonRestHttpMethodsFuzzer.description()).isNotNull();
        Assertions.assertThat(nonRestHttpMethodsFuzzer).hasToString(nonRestHttpMethodsFuzzer.getClass().getSimpleName());
        Assertions.assertThat(nonRestHttpMethodsFuzzer.skipForHttpMethods()).isEmpty();
    }
}
