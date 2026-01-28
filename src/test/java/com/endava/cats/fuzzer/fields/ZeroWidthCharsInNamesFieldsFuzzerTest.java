package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class ZeroWidthCharsInNamesFieldsFuzzerTest {
    private ZeroWidthCharsInNamesFieldsFuzzer zeroWidthCharsInNamesFieldsFuzzer;
    private SimpleExecutor simpleExecutor;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        zeroWidthCharsInNamesFieldsFuzzer = new ZeroWidthCharsInNamesFieldsFuzzer(simpleExecutor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(zeroWidthCharsInNamesFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(zeroWidthCharsInNamesFieldsFuzzer).hasToString(zeroWidthCharsInNamesFieldsFuzzer.getClass().getSimpleName());
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "null"}, nullValues = "null")
    void shouldNotRunWithEmptyPayload(String payload) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn(payload);
        zeroWidthCharsInNamesFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldNotRunWhenNoFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of());
        zeroWidthCharsInNamesFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldRunWhenFieldsPresent() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name", "lastName", "address#zip"));
        Mockito.when(data.getPayload()).thenReturn("""
                {
                    "name": "John",
                    "lastName": "Doe",
                    "address": {
                        "zip": "12345"
                    }
                }
                """);
        zeroWidthCharsInNamesFieldsFuzzer.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(18)).execute(Mockito.any());
    }

    @Test
    void shouldSkipForHttpMethods() {
        Assertions.assertThat(zeroWidthCharsInNamesFieldsFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
