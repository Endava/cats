package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class ZeroWidthCharsInNamesHeadersFuzzerTest {
    private ZeroWidthCharsInNamesHeadersFuzzer zeroWidthCharsInNamesHeadersFuzzer;
    private SimpleExecutor simpleExecutor;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        zeroWidthCharsInNamesHeadersFuzzer = new ZeroWidthCharsInNamesHeadersFuzzer(simpleExecutor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(zeroWidthCharsInNamesHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(zeroWidthCharsInNamesHeadersFuzzer).hasToString(zeroWidthCharsInNamesHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldNotRunSimpleExecutorWhenNoHeaders() {
        zeroWidthCharsInNamesHeadersFuzzer.fuzz(FuzzingData.builder().headers(Set.of()).build());
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldRunWhenHeadersArePresent() {
        zeroWidthCharsInNamesHeadersFuzzer.fuzz(FuzzingData.builder().headers(Set.of(CatsHeader.builder().name("test").value("value").build())).build());
        Mockito.verify(simpleExecutor, Mockito.times(11)).execute(Mockito.any());
    }
}
