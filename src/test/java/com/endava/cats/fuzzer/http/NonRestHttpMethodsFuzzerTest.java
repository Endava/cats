package com.endava.cats.fuzzer.http;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.PathItem;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(HttpMethodFuzzerUtil.class)
class NonRestHttpMethodsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @Autowired
    private HttpMethodFuzzerUtil httpMethodFuzzerUtil;

    @SpyBean
    private BuildProperties buildProperties;


    private NonRestHttpMethodsFuzzer nonRestHttpMethodsFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        nonRestHttpMethodsFuzzer = new NonRestHttpMethodsFuzzer(serviceCaller, testCaseListener, httpMethodFuzzerUtil);
    }

    @Test
    void shouldCallServiceAndReportErrorWhenServiceRespondsWith200() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(17)).reportError(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.eq(405), Mockito.eq(200));
    }

    @Test
    void shouldCallServiceAndReportInfoWhenServiceRespondsWith405() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(17)).reportInfo(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.eq(405));
    }

    @Test
    void shouldCallServiceAndReportWarnWhenServiceRespondsWith400() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(400).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(17)).reportWarn(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.eq(405), Mockito.eq(400));
    }

    @Test
    void shouldRunOncePerPath() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).path("/test").build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(17)).reportInfo(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.eq(405));
        Mockito.clearInvocations(testCaseListener);
        nonRestHttpMethodsFuzzer.fuzz(data);
        Mockito.verifyNoMoreInteractions(testCaseListener);
    }

    @Test
    void shouldOverrideMethods() {
        Assertions.assertThat(nonRestHttpMethodsFuzzer.description()).isNotNull();
        Assertions.assertThat(nonRestHttpMethodsFuzzer).hasToString(nonRestHttpMethodsFuzzer.getClass().getSimpleName());
        Assertions.assertThat(nonRestHttpMethodsFuzzer.skipFor()).isEmpty();
    }
}
