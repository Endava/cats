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

@QuarkusTest
class ExtraHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private ExtraHeadersFuzzer extraHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        extraHeadersFuzzer = new ExtraHeadersFuzzer(simpleExecutor);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void givenASetOfHeaders_whenCallingTheExtraHeadersFuzzer_thenTheResultsAreCorrectlyReported() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).
                responses(responses).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        extraHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX), Mockito.anyBoolean());

    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(extraHeadersFuzzer).hasToString(extraHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldNotSkipForAnyHttpMethod() {
        Assertions.assertThat(extraHeadersFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(extraHeadersFuzzer.description()).isNotBlank();
    }
}
