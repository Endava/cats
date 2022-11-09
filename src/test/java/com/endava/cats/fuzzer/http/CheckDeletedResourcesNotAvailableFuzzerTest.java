package com.endava.cats.fuzzer.http;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@QuarkusTest
class CheckDeletedResourcesNotAvailableFuzzerTest {

    private CheckDeletedResourcesNotAvailableFuzzer checkDeletedResourcesNotAvailableFuzzer;
    private CatsGlobalContext catsGlobalContext;
    @InjectSpy
    private TestCaseListener testCaseListener;
    @InjectSpy
    private SimpleExecutor simpleExecutor;
    private ServiceCaller serviceCaller;

    @BeforeEach
    void setup() {
        catsGlobalContext = Mockito.mock(CatsGlobalContext.class);
        checkDeletedResourcesNotAvailableFuzzer = new CheckDeletedResourcesNotAvailableFuzzer(simpleExecutor, catsGlobalContext, testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        ReflectionTestUtils.setField(simpleExecutor, "testCaseListener", testCaseListener);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());
        ReflectionTestUtils.setField(simpleExecutor, "serviceCaller", serviceCaller);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(checkDeletedResourcesNotAvailableFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(checkDeletedResourcesNotAvailableFuzzer.toString()).isNotBlank();
    }

    @CsvSource({"http://localhost:8080/relative-path,/relative-path", "http://localhost/relative-path,/relative-path", "/relative-path,/relative-path"})
    @ParameterizedTest
    void shouldReturnUrl(String url, String expected) {
        Assertions.assertThat(CheckDeletedResourcesNotAvailableFuzzer.getRelativePath(url)).isEqualTo(expected);
    }

    @Test
    void shouldNotRunWhenNotGetRequest() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        checkDeletedResourcesNotAvailableFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldNotRunWhenNoStoredDeleteRequests() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(catsGlobalContext.getSuccessfulDeletes()).thenReturn(Collections.emptySet());
        checkDeletedResourcesNotAvailableFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @ParameterizedTest
    @CsvSource({"400", "404", "410"})
    void shouldRunWhenGetAndStoredDeleteRequests(int respCode) {
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(respCode).build());
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(catsGlobalContext.getSuccessfulDeletes()).thenReturn(new HashSet<>(Set.of("http://localhost/path")));
        checkDeletedResourcesNotAvailableFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(Mockito.any());
        Assertions.assertThat(catsGlobalContext.getSuccessfulDeletes()).isEmpty();
    }

    @Test
    void shouldSkipAllMethodsButNotGet() {
        Assertions.assertThat(checkDeletedResourcesNotAvailableFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.HEAD, HttpMethod.PATCH, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.TRACE);
    }
}
