package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class MinGreaterThanMaxFieldsFuzzerTest {
    private MinGreaterThanMaxFieldsFuzzer minGreaterThanMaxFieldsFuzzer;
    private SimpleExecutor executor;

    @BeforeEach
    void setup() {
        executor = Mockito.mock(SimpleExecutor.class);
        minGreaterThanMaxFieldsFuzzer = new MinGreaterThanMaxFieldsFuzzer(executor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(minGreaterThanMaxFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(minGreaterThanMaxFieldsFuzzer).hasToString("MinGreaterThanMaxFieldsFuzzer");
    }

    @Test
    void shouldNotRunWhenNoMinMaxFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getReqSchema()).thenReturn(new Schema<String>());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("someField"));
        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @ParameterizedTest
    @CsvSource({"minPrice,someField", "maxPrice,someField", "minPrice,maxValue"})
    void shouldNotRunWhenOneBoundaryField(String field1, String field2) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExample("Example");
        Mockito.when(data.getReqSchema()).thenReturn(schema);
        Mockito.when(data.getPayload()).thenReturn("""
                    {
                        "minPrice": 50,
                        "maxPrice": 100,
                        "maxValue": 200,
                        "someField": 0
                    }
                """);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of(field1, field2));
        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldRunWhenMinMaxFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExample("Example");
        Mockito.when(data.getReqSchema()).thenReturn(schema);
        Mockito.when(data.getPayload()).thenReturn("""
                    {
                        "minPrice": 50,
                        "maxPrice": 100
                    }
                """);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("minPrice", "maxPrice"));
        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }
}
