package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.ResponseCodeFamily;
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
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class RemoveHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private RemoveHeadersFuzzer removeHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        removeHeadersFuzzer = new RemoveHeadersFuzzer(simpleExecutor);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void givenASetOfHeadersWithNoRequiredHeaders_whenApplyingTheRemoveHeadersFuzzer_thenTheHeadersAreProperlyFuzzed() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).
                responses(responses).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX), Mockito.anyBoolean());
    }

    @Test
    void givenASetOfHeadersWithRequiredHeaders_whenApplyingTheRemoveHeadersFuzzer_thenTheHeadersAreProperlyFuzzed() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("400", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").required(true).build())).
                responses(responses).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX), Mockito.anyBoolean());
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX), Mockito.anyBoolean());
    }

    @Test
    void givenASetOfHeaders_whenAnErrorOccursCallingTheService_thenTheErrorIsProperlyReported() {
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build()))
                .reqSchema(new StringSchema()).requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200")).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenThrow(new RuntimeException("something went wrong"));
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(removeHeadersFuzzer).hasToString(removeHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(removeHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotSkipForAnyHttpMethod() {
        Assertions.assertThat(removeHeadersFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldNotRunWhenNoHeaders() {
        FuzzingData data = FuzzingData.builder().headers(Collections.emptySet()).build();
        removeHeadersFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }
}
