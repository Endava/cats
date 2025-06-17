package com.endava.cats.fuzzer.fields;


import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

@QuarkusTest
class InvalidReferencesFieldsFuzzerTest {

    private InvalidReferencesFieldsFuzzer invalidReferencesFieldsFuzzer;
    private FilesArguments filesArguments;
    private SimpleExecutor simpleExecutor;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private ServiceCaller serviceCaller;


    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(simpleExecutor, "serviceCaller", serviceCaller);
        ReflectionTestUtils.setField(simpleExecutor, "testCaseListener", testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        invalidReferencesFieldsFuzzer = new InvalidReferencesFieldsFuzzer(filesArguments, simpleExecutor, testCaseListener);
    }

    @Test
    void shouldNotSkipForAnyMethod() {
        Assertions.assertThat(invalidReferencesFieldsFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldNotSkipForAnyField() {
        Assertions.assertThat(invalidReferencesFieldsFuzzer.skipForFields()).isEmpty();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(invalidReferencesFieldsFuzzer.toString()).isNotBlank();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(invalidReferencesFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotRunAnyExecutorWhenMethodWithBodyAndPathNotHaveVariables() {
        FuzzingData data = FuzzingData.builder().path("/test").method(HttpMethod.POST).build();
        invalidReferencesFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldRunSimpleExecutorForMethodWithBody() {
        FuzzingData data = FuzzingData.builder().path("/test/{id}/{sub}").method(HttpMethod.POST).build();
        invalidReferencesFieldsFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(46)).execute(Mockito.any());
    }

    @Test
    void shouldRunFieldsIteratorExecutorForMethodWithoutBody() {
        FuzzingData data = FuzzingData.builder().path("/test/{test}").method(HttpMethod.GET).build();
        invalidReferencesFieldsFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(23)).execute(Mockito.any());
    }

    @Test
    void shouldReportError() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field"));
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPath()).thenReturn("/test/{id}/{sub}");
        Mockito.doCallRealMethod().when(simpleExecutor).execute(Mockito.any());
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(500).httpMethod("POST").build());
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        invalidReferencesFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(46)).reportResultError(Mockito.any(), Mockito.eq(data),
                Mockito.eq("Unexpected response code: 500"),
                Mockito.eq("Request failed unexpectedly for http method [{}]: expected [{}], actual [{}]"),
                Mockito.eq(new Object[]{"POST", "4XX, 2XX", "500"}));
    }

    @ParameterizedTest
    @CsvSource({"222", "444"})
    void shouldReportInfo(int responseCode) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field"));
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPath()).thenReturn("/test/{id}/{sub}");
        Mockito.doCallRealMethod().when(simpleExecutor).execute(Mockito.any());
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(responseCode).httpMethod("POST").build());
        Mockito.doNothing().when(testCaseListener).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        invalidReferencesFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(46)).reportResultInfo(Mockito.any(), Mockito.eq(data),
                Mockito.eq("Response code expected: [{}]"),
                Mockito.eq(new Object[]{responseCode}));
    }
}
