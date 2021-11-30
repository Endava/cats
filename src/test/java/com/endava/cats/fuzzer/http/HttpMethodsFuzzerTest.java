package com.endava.cats.fuzzer.http;

import com.endava.cats.args.IgnoreArguments;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(HttpMethodFuzzerUtil.class)
class HttpMethodsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private IgnoreArguments ignoreArguments;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @Autowired
    private HttpMethodFuzzerUtil httpMethodFuzzerUtil;

    private HttpMethodsFuzzer httpMethodsFuzzer;

    @BeforeEach
    void setup() {
        httpMethodsFuzzer = new HttpMethodsFuzzer(serviceCaller, testCaseListener, httpMethodFuzzerUtil);
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
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportInfo(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.eq(405));
    }

    @Test
    void givenAnOperation_whenCallingTheHttpMethodsFuzzerAndTheServiceResponsesWithA2xx_thenResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().pathItem(new PathItem()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        httpMethodsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(7)).reportError(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.eq(405), Mockito.eq(200));
    }

    @Test
    void givenAHttpFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(httpMethodsFuzzer.description()).isNotNull();
        Assertions.assertThat(httpMethodsFuzzer).hasToString(httpMethodsFuzzer.getClass().getSimpleName());
        Assertions.assertThat(httpMethodsFuzzer.skipForHttpMethods()).isEmpty();
    }
}
