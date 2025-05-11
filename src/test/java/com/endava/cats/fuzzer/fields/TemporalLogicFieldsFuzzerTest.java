package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class TemporalLogicFieldsFuzzerTest {
    private TemporalLogicFieldsFuzzer temporalLogicFieldsFuzzer;
    private FieldsIteratorExecutor executor;

    @BeforeEach
    void setup() {
        executor = Mockito.mock(FieldsIteratorExecutor.class);
        temporalLogicFieldsFuzzer = new TemporalLogicFieldsFuzzer(executor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(temporalLogicFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(temporalLogicFieldsFuzzer).hasToString("TemporalLogicFieldsFuzzer");
    }

    @Test
    void shouldNotRunWhenNoTemporalFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getReqSchema()).thenReturn(new Schema<String>());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("someField"));
        temporalLogicFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldRunWhenTemporalFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExample("Example");
        Mockito.when(data.getReqSchema()).thenReturn(schema);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate"));
        temporalLogicFieldsFuzzer.fuzz(data);
        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }
}
