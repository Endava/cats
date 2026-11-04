package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

@QuarkusTest
class HttpMethodOverrideTunnelingFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private HttpMethodOverrideTunnelingFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new HttpMethodOverrideTunnelingFuzzer(simpleExecutor, testCaseListener);
    }

    @Test
    void shouldHaveDescriptionToStringAndSkippedMethods() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("HttpMethodOverrideTunnelingFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldSelectUndocumentedVerbInPriorityOrder() {
        Assertions.assertThat(HttpMethodOverrideTunnelingFuzzer.selectUndocumentedVerb(null)).contains("DELETE");

        PathItem withDelete = new PathItem().delete(new Operation());
        Assertions.assertThat(HttpMethodOverrideTunnelingFuzzer.selectUndocumentedVerb(withDelete)).contains("PUT");

        PathItem withDeleteAndPut = new PathItem().delete(new Operation()).put(new Operation());
        Assertions.assertThat(HttpMethodOverrideTunnelingFuzzer.selectUndocumentedVerb(withDeleteAndPut)).contains("PATCH");

        PathItem allPresent = new PathItem().delete(new Operation()).put(new Operation()).patch(new Operation());
        Assertions.assertThat(HttpMethodOverrideTunnelingFuzzer.selectUndocumentedVerb(allPresent)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "204, true",
            "500, true",
            "405, false",
            "200, false"
    })
    void shouldExecuteProbesOncePerPostPathAndReportOutcome(int statusCode, boolean expectError) {
        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .payload("{\"item\":\"book\"}")
                .pathItem(new PathItem().post(new Operation()))
                .requestContentTypes(List.of("application/json"))
                .headers(Set.of(CatsHeader.from("X-HTTP-Method-Override", "POST", false, null)))
                .build();

        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().responseCode(statusCode).body("{}").build());

        fuzzer.fuzz(data);
        fuzzer.fuzz(data);
        fuzzer.fuzz(FuzzingData.builder().path("/orders").method(HttpMethod.GET).build());

        Mockito.verify(serviceCaller, Mockito.times(3)).call(Mockito.any());
        if (expectError) {
            Mockito.verify(testCaseListener, Mockito.times(3)).reportResultError(
                    Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        } else {
            Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(
                    Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());
        }
    }
}
