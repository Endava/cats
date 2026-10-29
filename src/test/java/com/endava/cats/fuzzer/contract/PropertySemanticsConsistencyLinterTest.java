package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

@QuarkusTest
class PropertySemanticsConsistencyLinterTest {
    private TestCaseListener testCaseListener;
    private PropertySemanticsConsistencyLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new PropertySemanticsConsistencyLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenTimestampsAndMonetaryFieldsUseConsistentSafeTypes() {
        Schema<?> invoice = new Schema<>().type("object").properties(Map.of(
                "totalAmount", new Schema<>().type("integer").format("int64"),
                "createdAt", new Schema<>().type("string").format("date-time")
        ));
        Schema<?> receipt = new Schema<>().type("object").properties(Map.of(
                "updatedAt", new Schema<>().type("string").format("date-time")
        ));

        OpenAPI openAPI = new OpenAPI().components(new Components()
                .addSchemas("Invoice", invoice)
                .addSchemas("Receipt", receipt));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All semantic properties (timestamps and monetary fields) use consistent, safe types")
        );
    }

    @Test
    void shouldReportWarnWhenMonetaryFieldUsesFloatOrTimestampsDrift() {
        Schema<?> invoice = new Schema<>().type("object").properties(Map.of(
                "amount", new Schema<>().type("number").format("double"),
                "createdAt", new Schema<>().type("string").format("date-time")
        ));
        Schema<?> auditEntry = new Schema<>().type("object").properties(Map.of(
                "createdAt", new Schema<>().type("integer").format("int64")
        ));

        OpenAPI openAPI = new OpenAPI().components(new Components()
                .addSchemas("Invoice", invoice)
                .addSchemas("AuditEntry", auditEntry));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Property semantics consistency issues detected"),
                Mockito.argThat((String msg) -> msg.contains("Monetary property 'Invoice.amount' uses floating-point type 'number' (double)")
                        && msg.contains("Audit timestamp properties use inconsistent types/formats across schemas"))
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("PropertySemanticsConsistencyLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that audit timestamp properties share a consistent type/format across schemas and flags floating-point monetary fields");
    }
}
