package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class RemoveHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private RemoveHeadersFuzzer removeHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        removeHeadersFuzzer = new RemoveHeadersFuzzer(serviceCaller, testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void givenASetOfHeadersWithNoRequiredHeaders_whenApplyingTheRemoveHeadersFuzzer_thenTheHeadersAreProperlyFuzzed() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).
                responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenASetOfHeadersWithRequiredHeaders_whenApplyingTheRemoveHeadersFuzzer_thenTheHeadersAreProperlyFuzzed() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("400", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").required(true).build())).
                responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @Test
    void givenASetOfHeaders_whenAnErrorOccursCallingTheService_thenTheErrorIsProperlyReported() {
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenThrow(new RuntimeException());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportError(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void givenARemoveHeadersFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(removeHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(removeHeadersFuzzer).hasToString(removeHeadersFuzzer.getClass().getSimpleName());
        Assertions.assertThat(removeHeadersFuzzer.skipForHttpMethods()).isEmpty();
    }
}
