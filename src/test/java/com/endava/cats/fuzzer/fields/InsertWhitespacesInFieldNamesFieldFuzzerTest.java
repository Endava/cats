package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class InsertWhitespacesInFieldNamesFieldFuzzerTest {
    private SimpleExecutor simpleExecutor;

    private InsertWhitespacesInFieldNamesFieldFuzzer insertWhitespacesInFieldNamesFieldFuzzer;

    private CatsResponse catsResponse;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        insertWhitespacesInFieldNamesFieldFuzzer = new InsertWhitespacesInFieldNamesFieldFuzzer(simpleExecutor);
    }

    @Test
    void shouldNotRunForEmptyPayload() {
        insertWhitespacesInFieldNamesFieldFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldNotRunForFieldNotInPayload() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"field1\": \"value1\"}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field2"));
        insertWhitespacesInFieldNamesFieldFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldRunWhenFieldInPayload() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"field1\": \"value1\"}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field1"));
        insertWhitespacesInFieldNamesFieldFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor).execute(Mockito.any());
    }

    @Test
    void shouldHaveProperDescriptionAndToString() {
        Assertions.assertThat(insertWhitespacesInFieldNamesFieldFuzzer.description()).isNotNull();
        Assertions.assertThat(insertWhitespacesInFieldNamesFieldFuzzer).hasToString(insertWhitespacesInFieldNamesFieldFuzzer.getClass().getSimpleName());
    }
}
