package com.endava.cats.fuzzer.http;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@QuarkusTest
class DummyRequestFuzzerTest {
    private ServiceCaller serviceCaller;
    private CatsUtil catsUtil;
    @InjectSpy
    private TestCaseListener testCaseListener;

    private DummyRequestFuzzer dummyRequestFuzzer;

    @BeforeEach
    void setup() {
        catsUtil = new CatsUtil(null);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        dummyRequestFuzzer = new DummyRequestFuzzer(serviceCaller, testCaseListener, catsUtil);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void givenAHttpMethodWithoutPayload_whenApplyingTheMalformedJsonFuzzer_thenTheResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(400).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        dummyRequestFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.anyString());
    }

    @Test
    void givenAHttpMethodWithPayload_whenApplyingTheMalformedJsonFuzzer_thenTheResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.POST).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(400).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        dummyRequestFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @Test
    void givenAMalformedJsonFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(dummyRequestFuzzer.description()).isNotNull();
        Assertions.assertThat(dummyRequestFuzzer).hasToString(dummyRequestFuzzer.getClass().getSimpleName());
        Assertions.assertThat(dummyRequestFuzzer.skipForHttpMethods()).isEmpty();
        Assertions.assertThat(dummyRequestFuzzer.getPayload(FuzzingData.builder().build())).isEqualTo(DummyRequestFuzzer.DUMMY_JSON);
    }
}
