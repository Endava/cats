package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class BaseHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;

    private BaseHeadersFuzzer baseHeadersFuzzer;

    private FilterArguments filterArguments;

    private CatsResponse catsResponse;


    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        HeadersIteratorExecutor headersIteratorExecutor = new HeadersIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), filterArguments);
        baseHeadersFuzzer = new MyBaseHeadersFuzzer(headersIteratorExecutor);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
    }

    @Test
    void givenAConcreteBaseHeadersFuzzerInstanceWithNoMandatoryHeader_whenExecutingTheFuzzMethod_thenTheFuzzingLogicIsProperlyExecuted() {
        FuzzingData data = this.createData(false);
        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX), Mockito.eq(true));
    }

    @Test
    void givenAConcreteBaseHeadersFuzzerInstanceWithMandatoryHeader_whenExecutingTheFuzzMethod_thenTheFuzzingLogicIsProperlyExecuted() {
        FuzzingData data = this.createData(true);
        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX), Mockito.eq(true));
    }

    @Test
    void shouldNotRunWhenNoHeaders() {
        FuzzingData data = FuzzingData.builder().headers(Set.of(CatsHeader.builder().name("jwt").value("jwt").build())).reqSchema(new StringSchema())
                .requestContentTypes(List.of("application/json")).build();
        Mockito.doCallRealMethod().when(serviceCaller).isAuthenticationHeader(Mockito.any());
        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldNotRunForIgnoredHeaders() {
        FuzzingData data = createData(true);
        data.getHeaders().add(CatsHeader.builder().name("skippedHeader").value("skippedValue").required(true).build());
        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX), Mockito.eq(true));

        Mockito.when(filterArguments.getSkipHeaders()).thenReturn(List.of("skippedHeader"));
        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX), Mockito.eq(true));
    }

    private FuzzingData createData(boolean requiredHeaders) {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(Set.of(CatsHeader.builder().name("header").value("value").required(requiredHeaders).build())))
                .responseCodes(Set.of("200", "202")).reqSchema(new StringSchema())
                .responses(responses).requestContentTypes(List.of("application/json")).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        return data;
    }

    static class MyBaseHeadersFuzzer extends BaseHeadersFuzzer {

        public MyBaseHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
            super(headersIteratorExecutor);
        }

        @Override
        public BaseHeadersFuzzerContext createFuzzerContext() {
            return BaseHeadersFuzzerContext.builder()
                    .matchResponseSchema(true)
                    .fuzzStrategy(Collections.singletonList(FuzzingStrategy.replace()))
                    .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                    .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.TWOXX)
                    .typeOfDataSentToTheService("my data")
                    .build();
        }
    }
}
