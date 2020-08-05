package com.endava.cats.fuzzer.http;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class HttpMethodsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @MockBean
    private BuildProperties buildProperties;


    private HttpMethodsFuzzer httpMethodsFuzzer;

    @BeforeEach
    void setup() {
        httpMethodsFuzzer = new HttpMethodsFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenAGetOperationImplemented_whenCallingTheHttpMethodsFuzzer_thenResultsAreCorrectlyReported() {
        PathItem item = new PathItem();
        item.setGet(new Operation());
        item.setPost(new Operation());
        item.setTrace(new Operation());
        item.setPatch(new Operation());
        item.setDelete(new Operation());
        item.setHead(new Operation());
        item.setPut(new Operation());
        FuzzingData data = FuzzingData.builder().pathItem(item).build();
        httpMethodsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzer_thenResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).build();
        Mockito.when(serviceCaller.post(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.get(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.delete(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.trace(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.head(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.patch(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.put(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportInfo(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.eq(405));
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzerAndTheServiceResponsesWithA2xx_thenResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.post(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.get(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.delete(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.trace(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.head(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.patch(Mockito.any())).thenReturn(catsResponse);
        Mockito.when(serviceCaller.put(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportError(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.eq(405), Mockito.eq(200));
    }

    @Test
    void givenAHttpFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(httpMethodsFuzzer.description()).isNotNull();
        Assertions.assertThat(httpMethodsFuzzer).hasToString(httpMethodsFuzzer.getClass().getSimpleName());
        Assertions.assertThat(httpMethodsFuzzer.skipFor()).isEmpty();
    }
}
