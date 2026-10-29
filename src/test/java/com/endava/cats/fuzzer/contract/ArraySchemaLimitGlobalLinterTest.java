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
class ArraySchemaLimitGlobalLinterTest {
    private TestCaseListener testCaseListener;
    private ArraySchemaLimitGlobalLinter linter;
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
        linter = new ArraySchemaLimitGlobalLinter(testCaseListener, collector);
    }

    @Test
    void shouldValidateGlobalArraySchemas() {
        var context = linter.getContext();
        SchemaLocation global = new SchemaLocation(null, null, "components.schemas.Order.tags", null);
        SchemaLocation inline = new SchemaLocation("/orders", "POST", "paths./orders.tags", null);

        Assertions.assertThat(context.filter().test(global, Mockito.mock(FuzzingData.class))).isTrue();
        Assertions.assertThat(context.filter().test(inline, Mockito.mock(FuzzingData.class))).isFalse();
        Assertions.assertThat(context.validator().test(new Schema<>().maxItems(50))).isTrue();
        Assertions.assertThat(context.validator().test(new Schema<>().maxItems(0))).isFalse();
        Assertions.assertThat(context.validator().test(new Schema<>())).isFalse();
    }

    @Test
    void shouldReportWarnWhenGlobalArrayHasNoMaxItems() {
        SchemaLocation global = new SchemaLocation(null, null, "components.schemas.Order.tags", null);
        Mockito.when(collector.getArraySchemas()).thenReturn(Map.of(global, new Schema<>().type("array")));

        FuzzingData data = FuzzingData.builder().path("/orders").method(HttpMethod.POST).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Array schemas without maxItems found"),
                Mockito.contains("components.schemas.Order.tags")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("ArraySchemaLimitGlobalLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that all global array schemas specify maxItems");
    }
}
