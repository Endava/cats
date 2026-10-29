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
import java.util.List;
import java.util.Map;

@QuarkusTest
class NumericSchemaBoundsGlobalLinterTest {
    private TestCaseListener testCaseListener;
    private NumericSchemaBoundsGlobalLinter linter;
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
        linter = new NumericSchemaBoundsGlobalLinter(testCaseListener, collector);
    }

    @Test
    void shouldValidateGlobalNumericSchemas() {
        var context = linter.getContext();
        SchemaLocation global = new SchemaLocation(null, null, "components.schemas.Order.quantity", null);
        SchemaLocation inline = new SchemaLocation("/orders", "POST", "paths./orders.quantity", null);

        Assertions.assertThat(context.filter().test(global, Mockito.mock(FuzzingData.class))).isTrue();
        Assertions.assertThat(context.filter().test(inline, Mockito.mock(FuzzingData.class))).isFalse();
        Assertions.assertThat(context.validator().test(new Schema<>().minimum(BigDecimal.ZERO).maximum(BigDecimal.TEN))).isTrue();
        Assertions.assertThat(context.validator().test(new Schema<>().minimum(BigDecimal.ZERO))).isFalse();
        Assertions.assertThat(context.validator().test(new Schema<>()._enum(List.of(1, 2, 3)))).isTrue();
        Assertions.assertThat(context.validator().test(new Schema<>())).isFalse();
        Assertions.assertThat(context.format().apply(global, new Schema<>()))
                .contains("components.schemas.Order.quantity", "does not specify minimum/maximum bounds or enum");
    }

    @Test
    void shouldReportWarnWhenGlobalNumericSchemaHasNoBounds() {
        SchemaLocation global = new SchemaLocation(null, null, "components.schemas.Order.quantity", null);
        Mockito.when(collector.getNumericSchemas()).thenReturn(Map.of(global, new Schema<>().type("integer")));

        FuzzingData data = FuzzingData.builder().path("/orders").method(HttpMethod.POST).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Numeric schemas without minimum/maximum bounds or enum found"),
                Mockito.contains("components.schemas.Order.quantity")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("NumericSchemaBoundsGlobalLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that all global numeric schemas specify minimum and maximum bounds or enum");
    }
}
