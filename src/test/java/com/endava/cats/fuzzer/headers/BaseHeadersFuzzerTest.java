package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class BaseHeadersFuzzerTest {


    @Mock
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;


    private BaseHeadersFuzzer baseHeadersFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        baseHeadersFuzzer = new MyBaseHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenAConcreteBaseHeadersFuzzerInstanceWithNoMandatoryHeader_whenExecutingTheFuzzMethod_thenTheFuzzingLogicIsProperlyExecuted() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).
                responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);

        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenAConcreteBaseHeadersFuzzerInstanceWithMandatoryHeader_whenExecutingTheFuzzMethod_thenTheFuzzingLogicIsProperlyExecuted() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").required(true).build())).
                responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);

        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        baseHeadersFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
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
        protected FuzzingStrategy fuzzStrategy() {
            return FuzzingStrategy.replace();
        }

        @Override
        public String description() {
            return null;
        }
    }
}
