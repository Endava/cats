package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.KeyValuePair;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@QuarkusTest
class ResponseHeadersMatchContractHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private ResponseHeadersMatchContractHeadersFuzzer responseHeadersMatchContractHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        responseHeadersMatchContractHeadersFuzzer = new ResponseHeadersMatchContractHeadersFuzzer(testCaseListener, simpleExecutor);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(responseHeadersMatchContractHeadersFuzzer.description()).isEqualTo("send a request with all fields and headers populated and checks if the response headers match the ones defined in the contract");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(responseHeadersMatchContractHeadersFuzzer).hasToString(responseHeadersMatchContractHeadersFuzzer.getClass().getSimpleName());
    }


    @Test
    void shouldNotRunWhenNoResponseHeaders() {
        FuzzingData data = FuzzingData.builder().responseHeaders(Collections.emptyMap())
                .requestContentTypes(Collections.singletonList("application/json")).reqSchema(new StringSchema()).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(Collections.emptyList()).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        responseHeadersMatchContractHeadersFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldReportMissingHeaders() {
        FuzzingData data = FuzzingData.builder().responseHeaders(Map.of("200", Set.of("missingHeader", "anotherMissingHeader")))
                .requestContentTypes(Collections.singletonList("application/json")).reqSchema(new StringSchema()).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(List.of(new KeyValuePair<>("header1", "value1"))).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        responseHeadersMatchContractHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.eq("The following response headers defined in the contract are missing: {}"), Mockito.eq(new TreeSet<>(Set.of("anotherMissingHeader", "missingHeader")).toArray()));
    }

    @Test
    void shouldNotReportMissingHeaders() {
        FuzzingData data = FuzzingData.builder().responseHeaders(Map.of("200", Set.of("notMissing", "notMissingAlso")))
                .requestContentTypes(Collections.singletonList("application/json")).reqSchema(new StringSchema()).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(List.of(new KeyValuePair<>("notMissing", "value1"), new KeyValuePair<>("notMissingAlso", "value2"))).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        responseHeadersMatchContractHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.any(), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));

    }
}
