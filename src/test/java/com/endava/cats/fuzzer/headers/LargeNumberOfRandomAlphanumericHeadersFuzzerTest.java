package com.endava.cats.fuzzer.headers;


import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class LargeNumberOfRandomAlphanumericHeadersFuzzerTest {

    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private LargeNumberOfRandomAlphanumericHeadersFuzzer largeNumberOfRandomAlphanumericHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        Mockito.when(processingArguments.getRandomHeadersNumber()).thenReturn(10000);
        largeNumberOfRandomAlphanumericHeadersFuzzer = new LargeNumberOfRandomAlphanumericHeadersFuzzer(simpleExecutor, testCaseListener, processingArguments);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(largeNumberOfRandomAlphanumericHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldReturnRandomHeaderValues() {
        Assertions.assertThat(largeNumberOfRandomAlphanumericHeadersFuzzer.randomHeadersValueFunction().apply(10)).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"201", "400"})
    void shouldReportInfoWhenNot5XX(int code) {
        FuzzingData data = setupSimpleFuzzingData();

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(code).build());
        largeNumberOfRandomAlphanumericHeadersFuzzer.fuzz(data);
        Mockito.doNothing().when(testCaseListener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        Mockito.verify(testCaseListener).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Request returned as expected for http method [{}] with response code [{}]"), Mockito.any());
    }

    @Test
    void shouldReportErrorWhen5XX() {
        FuzzingData data = setupSimpleFuzzingData();

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(500).build());
        largeNumberOfRandomAlphanumericHeadersFuzzer.fuzz(data);
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.verify(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Unexpected Response Code: 500"),
                Mockito.eq("Request failed unexpectedly for http method [{}]: expected [{}], actual [{}]"), Mockito.any());

    }

    private static FuzzingData setupSimpleFuzzingData() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        return FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).
                responses(responses).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
    }
}
