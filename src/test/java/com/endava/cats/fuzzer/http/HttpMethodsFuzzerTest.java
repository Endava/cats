package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.Operation;
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
        httpMethodsFuzzer = new HttpMethodsFuzzer(httpMethodFuzzerUtil);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        ReflectionTestUtils.setField(httpMethodFuzzerUtil, "simpleExecutor", simpleExecutor);
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
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").headers(List.of(new KeyValuePair<>("Allow", "GET, PUT, DELETE"))).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405}));
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzerAndTheServiceResponsesWithA2xx_thenResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 200}));
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(httpMethodsFuzzer).hasToString(httpMethodsFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(httpMethodsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotSkipForAnyHttpMethod() {
        Assertions.assertThat(httpMethodsFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldNotFuzzSamePathTwice() {
        FuzzingData data = FuzzingData.builder().path("/pet").pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 200}));
        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 200}));
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzerAndTheServiceResponsesWithNon2xxNon405_thenWarningIsReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(500).httpMethod("POST").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405, 500}));
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzerAndTheServiceResponsesWith405ButMissingAllowHeader_thenWarningIsReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").headers(List.of()).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq("POST"), AdditionalMatchers.aryEq(new Object[]{405}));
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzerAndTheServiceResponsesWith405AndAllowHeaderContainsMethod_thenWarningIsReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").headers(List.of(new KeyValuePair<>("Allow", "GET, POST, PUT"))).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("POST"), AdditionalMatchers.aryEq(new Object[]{405, "POST"}));
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzerAndTheServiceResponsesWith405AndValidAllowHeader_thenInfoIsReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(405).httpMethod("POST").headers(List.of(new KeyValuePair<>("Allow", "GET, PUT, DELETE"))).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString(), AdditionalMatchers.aryEq(new Object[]{"POST", 405}));
    }
}
