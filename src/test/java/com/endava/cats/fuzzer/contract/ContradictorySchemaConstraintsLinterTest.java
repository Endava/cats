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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@QuarkusTest
class ContradictorySchemaConstraintsLinterTest {
    private TestCaseListener testCaseListener;
    private ContradictorySchemaConstraintsLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new ContradictorySchemaConstraintsLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenSchemasHaveConsistentConstraints() {
        Schema<?> prop = new Schema<>().type("string").minLength(1).maxLength(20).pattern("^[a-z]+$")._enum(List.of("abc"))._default("abc");
        Schema<?> obj = new Schema<>().type("object").properties(Map.of("name", prop)).required(List.of("name"));
        OpenAPI openAPI = new OpenAPI().components(new Components().addSchemas("ValidItem", obj));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All schemas have consistent and valid constraints")
        );
    }

    @Test
    void shouldReportErrorWhenSchemaConstraintsAreContradictory() {
        Schema<?> badString = new Schema<>().type("string").minLength(10).maxLength(5).pattern("[unclosed")._enum(List.of("A", "B"))._default("C");
        Schema<?> badNumber = new Schema<>().type("integer").minimum(BigDecimal.valueOf(100)).maximum(BigDecimal.valueOf(10));
        Schema<?> badArray = new Schema<>().type("array").minItems(5).maxItems(2).items(new Schema<>().type("string"));
        Schema<?> badObj = new Schema<>().type("object")
                .properties(Map.of("str", badString, "num", badNumber, "arr", badArray))
                .required(List.of("str", "ghostField"));

        OpenAPI openAPI = new OpenAPI().components(new Components().addSchemas("BrokenItem", badObj));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Contradictory or broken schema constraints found"),
                Mockito.argThat((String msg) -> msg.contains("minLength (10) greater than maxLength (5)")
                        && msg.contains("invalid regex pattern '[unclosed'")
                        && msg.contains("default value 'C' not present in enum")
                        && msg.contains("minimum (100) greater than maximum (10)")
                        && msg.contains("minItems (5) greater than maxItems (2)")
                        && msg.contains("marks property 'ghostField' as required, but it is not defined in properties"))
        );
    }

    @Test
    void shouldReturnSimpleClassNameAndDescription() {
        Assertions.assertThat(linter).hasToString("ContradictorySchemaConstraintsLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("detects contradictory or invalid schema constraints (min > max bounds, invalid regex patterns, undeclared required fields, and invalid enum defaults/examples)");
    }

    @Test
    void shouldSkipWhenOpenApiNullAndCheckNegativeLengthsMinPropertiesAndExampleInEnum() {
        linter.process(FuzzingData.builder().build());
        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.eq("OpenAPI specification not available"));

        Schema<?> negLengths = new Schema<>().type("string").minLength(-2).maxLength(-1)
                ._enum(List.of("X", "Y")).example("Z");
        Schema<?> badPropCount = new Schema<>().type("object").minProperties(5).maxProperties(2);
        Schema<?> allOfRequired = new Schema<>().type("object")
                .allOf(List.of(new Schema<>().type("object")
                        .properties(Map.of("a", new Schema<>().type("string")))
                        .required(List.of("b"))));

        OpenAPI openAPI = new OpenAPI().components(new Components()
                .addSchemas("NegLengths", negLengths)
                .addSchemas("BadPropCount", badPropCount)
                .addSchemas("AllOfRequired", allOfRequired));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.process(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Contradictory or broken schema constraints found"),
                Mockito.argThat((String msg) -> msg.contains("has negative minLength (-2)")
                        && msg.contains("has negative maxLength (-1)")
                        && msg.contains("has example value 'Z' not present in enum")
                        && msg.contains("has minProperties (5) greater than maxProperties (2)")
                        && !msg.contains("AllOfRequired")));
    }
}
