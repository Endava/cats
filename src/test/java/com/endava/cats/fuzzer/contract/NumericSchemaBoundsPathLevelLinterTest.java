package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.collector.NumericSchemaCollector;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;

@QuarkusTest
class NumericSchemaBoundsPathLevelLinterTest {
    private TestCaseListener testCaseListener;
    private NumericSchemaBoundsPathLevelLinter linter;
    private NumericSchemaCollector collector;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        collector = Mockito.mock(NumericSchemaCollector.class);
        linter = new NumericSchemaBoundsPathLevelLinter(testCaseListener, collector);
    }

    @Test
    void shouldValidatePathLevelNumericSchemas() {
        SchemaLocation loc = new SchemaLocation("/orders", "POST", "paths./orders.amount", null);
        Mockito.when(collector.getNumericSchemas()).thenReturn(Map.of(
                loc, new Schema<>().type("number").minimum(BigDecimal.ONE).maximum(BigDecimal.TEN)
        ));

        FuzzingData data = FuzzingData.builder().path("/orders").method(HttpMethod.POST).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All numeric schemas have minimum/maximum bounds or enum defined")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("NumericSchemaBoundsPathLevelLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that numeric schemas specify minimum and maximum bounds or enum for inline schemas of the current path/method");
    }
}
