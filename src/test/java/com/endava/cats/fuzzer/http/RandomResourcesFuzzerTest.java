package com.endava.cats.fuzzer.http;

import com.endava.cats.args.FilesArguments;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;

@QuarkusTest
class RandomResourcesFuzzerTest {

    private ServiceCaller serviceCaller;
    private FilesArguments filesArguments;
    @InjectSpy
    private TestCaseListener testCaseListener;

    private RandomResourcesFuzzer randomResourcesFuzzer;

    @BeforeEach
    public void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        randomResourcesFuzzer = new RandomResourcesFuzzer(simpleExecutor, filesArguments);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunForPathsWithNoVariables() {
        randomResourcesFuzzer.fuzz(FuzzingData.builder().path("/test").build());

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldNotReplaceUrlParams() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).path("/test/{id}/another/{urlParam}").
                reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"id\":\"d46df8b7-7d69-4bb4-b63b-88c3ebe0e1b8\"}");
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(404).build();

        Mockito.when(filesArguments.getUrlParam("urlParam")).thenReturn("urlParamValue");
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.anyBoolean());
        randomResourcesFuzzer.fuzz(data);
        Mockito.verify(serviceCaller, Mockito.times(10)).call(argThat(serviceData -> !serviceData.getPayload().contains("urlParamValue")));
        Mockito.verify(testCaseListener, Mockito.times(10)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX_NF), Mockito.anyBoolean());
    }

    @ParameterizedTest
    @CsvSource({"{\"id\":12345}", "{\"id\":\"d46df8b7-7d69-4bb4-b63b-88c3ebe0e1b8\"}", "{\"id\":\"someString\"}"})
    void shouldRunForPathsWithVariables(String payload) {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).path("/test/{id}").
                reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        ReflectionTestUtils.setField(data, "processedPayload", payload);
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(404).build();

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        randomResourcesFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(10)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX_NF), Mockito.anyBoolean());
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenPathParamNotDeclared() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).path("/test/{id}").
                reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
        ReflectionTestUtils.setField(data, "processedPayload", "{}");

        Assertions.assertThatThrownBy(() -> randomResourcesFuzzer.fuzz(data)).isInstanceOf(IllegalStateException.class).hasMessage("OpenAPI spec is missing definition for id");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(randomResourcesFuzzer).hasToString(randomResourcesFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(randomResourcesFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldSkipForNonHttpBodyMethods() {
        Assertions.assertThat(randomResourcesFuzzer.skipForHttpMethods())
                .containsOnly(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.TRACE);
    }

}
