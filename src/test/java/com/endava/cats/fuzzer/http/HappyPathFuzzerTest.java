package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class HappyPathFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;

    private HappyPathFuzzer happyFuzzer;

    private SimpleExecutor simpleExecutor;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        happyFuzzer = new HappyPathFuzzer(simpleExecutor);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void givenARequest_whenCallingTheHappyFuzzer_thenTestCasesAreCorrectlyExecuted() {
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().path("path1").method(HttpMethod.POST).payload("{'field':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).reqSchema(new StringSchema())
                .requestContentTypes(List.of("application/json")).build();

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        happyFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX), Mockito.anyBoolean(), Mockito.eq(true));
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(happyFuzzer).hasToString(happyFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(happyFuzzer.description()).isNotBlank();
    }

    @Test
    void givenARequest_whenCallingTheHappyFuzzerAndAnErrorOccurs_thenTestCasesAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().path("path1").method(HttpMethod.POST).payload("{'field':'oldValue'}")
                .reqSchema(new StringSchema()).responseCodes(Collections.singleton("200"))
                .requestContentTypes(List.of("application/json")).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenThrow(new RuntimeException("something went wrong"));

        happyFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }
}
