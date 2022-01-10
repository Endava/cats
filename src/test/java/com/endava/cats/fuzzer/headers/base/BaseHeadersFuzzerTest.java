package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.google.common.collect.Sets;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class BaseHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;

    private BaseHeadersFuzzer baseHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        baseHeadersFuzzer = new MyBaseHeadersFuzzer(serviceCaller, testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void givenAConcreteBaseHeadersFuzzerInstanceWithNoMandatoryHeader_whenExecutingTheFuzzMethod_thenTheFuzzingLogicIsProperlyExecuted() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).
                responses(responses).responseCodes(Sets.newHashSet("200", "202")).reqSchema(new StringSchema()).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX), Mockito.eq(true));
    }

    @Test
    void givenAConcreteBaseHeadersFuzzerInstanceWithMandatoryHeader_whenExecutingTheFuzzMethod_thenTheFuzzingLogicIsProperlyExecuted() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").required(true).build()))
                .responseCodes(Sets.newHashSet("200", "202")).reqSchema(new StringSchema())
                .responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX), Mockito.eq(true));
    }

    @Test
    void shouldNotRunWhenNoHeaders() {
        FuzzingData data = FuzzingData.builder().headers(Set.of(CatsHeader.builder().name("jwt").value("jwt").build())).reqSchema(new StringSchema()).build();
        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    static class MyBaseHeadersFuzzer extends BaseHeadersFuzzer {

        public MyBaseHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
            super(sc, lr);
        }

        @Override
        protected String typeOfDataSentToTheService() {
            return "my data";
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed() {
            return ResponseCodeFamily.FOURXX;
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpForOptionalHeadersFuzzed() {
            return ResponseCodeFamily.TWOXX;
        }

        @Override
        protected List<FuzzingStrategy> fuzzStrategy() {
            return Collections.singletonList(FuzzingStrategy.replace());
        }

        @Override
        public String description() {
            return null;
        }
    }
}
