package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
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

import java.util.List;
import java.util.Set;

@QuarkusTest
class ZeroIntegerBodyFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private ZeroIntegerBodyFuzzer zeroIntegerBodyFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        zeroIntegerBodyFuzzer = new ZeroIntegerBodyFuzzer(simpleExecutor);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunForEmptyPayload() {
        zeroIntegerBodyFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenAHttpMethodWithPayload_whenApplyingTheMalformedJsonFuzzer_thenTheResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.POST).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).responseCodes(Set.of("400")).build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"id\": 1}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(400).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        zeroIntegerBodyFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.eq(true));
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(zeroIntegerBodyFuzzer).hasToString(zeroIntegerBodyFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(zeroIntegerBodyFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldSkipForNonHttpBodyMethods() {
        Assertions.assertThat(zeroIntegerBodyFuzzer.skipForHttpMethods()).contains(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    void shouldHaveZeroIntegerPayload() {
        Assertions.assertThat(zeroIntegerBodyFuzzer.getPayload(null)).isEqualTo("0");
    }
}
