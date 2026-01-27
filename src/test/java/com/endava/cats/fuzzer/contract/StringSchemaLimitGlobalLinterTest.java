package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.collector.StringSchemaCollector;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

@QuarkusTest
class StringSchemaLimitGlobalLinterTest {

    private TestCaseListener testCaseListener;
    private StringSchemaLimitGlobalLinter stringSchemaLimitGlobalLinter;
    private StringSchemaCollector stringSchemaCollector;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        stringSchemaCollector = Mockito.mock(StringSchemaCollector.class);
        stringSchemaLimitGlobalLinter = new StringSchemaLimitGlobalLinter(testCaseListener, stringSchemaCollector);
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(stringSchemaLimitGlobalLinter.description())
                .isEqualTo("verifies that all string schemas specify either maxLength or enum");
    }

    @Test
    void shouldReturnRunKey() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        stringSchemaLimitGlobalLinter.fuzz(data); // Indirectly tests runKey
        Assertions.assertThat(stringSchemaLimitGlobalLinter.getContext().runKeyProvider().apply(data))
                .isEqualTo("global-string-schema-limit-linter");
    }

    @Test
    void shouldCollectStringSchemasForGlobalComponents() {
        Map<SchemaLocation, Schema<?>> mockSchemas = Map.of(
                new SchemaLocation(null, null, null, null), new Schema()
        );
        Mockito.when(stringSchemaCollector.getStringSchemas()).thenReturn(mockSchemas);

        Map<SchemaLocation, Schema<?>> result = stringSchemaLimitGlobalLinter.getContext().collector().get();

        Assertions.assertThat(result).containsKey(new SchemaLocation(null, null, null, null));
        Assertions.assertThat(result.get(new SchemaLocation(null, null, null, null))).isNotNull();
    }

    @Test
    void shouldHandleEmptyStringSchemas() {
        Mockito.when(stringSchemaCollector.getStringSchemas()).thenReturn(Map.of());

        Map<SchemaLocation, Schema<?>> result = stringSchemaLimitGlobalLinter.getContext().collector().get();

        Assertions.assertThat(result).isEmpty();
    }

    @Test
    void shouldExecuteTestListener() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Map<SchemaLocation, Schema<?>> mockSchemas = Map.of(
                new SchemaLocation(null, null, null, null), new Schema()
        );
        Mockito.when(stringSchemaCollector.getStringSchemas()).thenReturn(mockSchemas);

        stringSchemaLimitGlobalLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
