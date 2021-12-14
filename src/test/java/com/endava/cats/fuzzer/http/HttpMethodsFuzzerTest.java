package com.endava.cats.fuzzer.http;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;

@QuarkusTest
class HttpMethodsFuzzerTest {
    @Inject
    HttpMethodFuzzerUtil httpMethodFuzzerUtil;
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private HttpMethodsFuzzer httpMethodsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        httpMethodsFuzzer = new HttpMethodsFuzzer(serviceCaller, testCaseListener, httpMethodFuzzerUtil);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
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
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportInfo(Mockito.any(), Mockito.anyString(),
                AdditionalMatchers.aryEq(new Object[]{"POST", 405}));
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzerAndTheServiceResponsesWithA2xx_thenResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportError(Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 200}));
    }

    @Test
    void givenAHttpFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(httpMethodsFuzzer.description()).isNotNull();
        Assertions.assertThat(httpMethodsFuzzer).hasToString(httpMethodsFuzzer.getClass().getSimpleName());
        Assertions.assertThat(httpMethodsFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldNotFuzzSamePathTwice() {
        FuzzingData data = FuzzingData.builder().path("/pet").pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportError(Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 200}));
        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportError(Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 200}));
    }
}
