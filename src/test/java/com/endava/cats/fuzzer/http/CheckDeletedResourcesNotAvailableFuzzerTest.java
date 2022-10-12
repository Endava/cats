package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@QuarkusTest
class CheckDeletedResourcesNotAvailableFuzzerTest {

    private CheckDeletedResourcesNotAvailableFuzzer checkDeletedResourcesNotAvailableFuzzer;
    private SimpleExecutor simpleExecutor;
    private CatsGlobalContext catsGlobalContext;


    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        catsGlobalContext = Mockito.mock(CatsGlobalContext.class);
        checkDeletedResourcesNotAvailableFuzzer = new CheckDeletedResourcesNotAvailableFuzzer(simpleExecutor, catsGlobalContext);
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

    @Test
    void shouldRunWhenGetAndStoredDeleteRequests() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(catsGlobalContext.getSuccessfulDeletes()).thenReturn(new HashSet<>(Set.of("http://localhost/path")));
        checkDeletedResourcesNotAvailableFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(Mockito.any());
        Assertions.assertThat(catsGlobalContext.getSuccessfulDeletes()).isEmpty();
    }
}
