package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.collector.ArraySchemaCollector;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

@QuarkusTest
class ArraySchemaLimitPathLevelLinterTest {
    private TestCaseListener testCaseListener;
    private ArraySchemaLimitPathLevelLinter linter;
    private ArraySchemaCollector collector;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        collector = Mockito.mock(ArraySchemaCollector.class);
        linter = new ArraySchemaLimitPathLevelLinter(testCaseListener, collector);
    }

    @Test
    void shouldReportInfoWhenInlineArrayHasMaxItems() {
        SchemaLocation loc = new SchemaLocation("/orders", "POST", "paths./orders.tags", null);
        Mockito.when(collector.getArraySchemas()).thenReturn(Map.of(loc, new Schema<>().type("array").maxItems(100)));

        FuzzingData data = FuzzingData.builder().path("/orders").method(HttpMethod.POST).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All array schemas have maxItems defined")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("ArraySchemaLimitPathLevelLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that array schemas specify maxItems for inline schemas of the current path/method");
    }
}
