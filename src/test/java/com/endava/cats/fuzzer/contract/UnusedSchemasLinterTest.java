package com.endava.cats.fuzzer.contract;

import com.endava.cats.context.CatsGlobalContext;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class UnusedSchemasLinterTest {
    private TestCaseListener testCaseListener;
    private CatsGlobalContext catsGlobalContext;
    private UnusedSchemasLinter unusedSchemasLinter;

    @BeforeEach
    void setup() {
        catsGlobalContext = Mockito.mock(CatsGlobalContext.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        unusedSchemasLinter = new UnusedSchemasLinter(testCaseListener, catsGlobalContext);
    }

    @Test
    void shouldReportWarnWhenUnusedSchemasExist() {
        Map<String, Schema> schemas = new HashMap<>();
        schemas.put("mySchema", new Schema<>());
        Components components = new Components();
        components.setSchemas(schemas);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();

        unusedSchemasLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Unused component schemas found"),
                Mockito.contains("- mySchema")
        );
    }

    @Test
    void shouldReportInfoWhenNoSchemasDefined() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        unusedSchemasLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("No schemas defined")
        );
    }

    @Test
    void shouldReportInfoWhenAllSchemasUsed() {
        Map<String, Schema> schemas = new HashMap<>();
        schemas.put("usedSchema", new Schema<>());
        Components components = new Components();
        components.setSchemas(schemas);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of("usedSchema"));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        unusedSchemasLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("All schemas in components.schemas are used at least once")
        );
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(unusedSchemasLinter).hasToString(unusedSchemasLinter.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(unusedSchemasLinter.description()).isEqualTo(
                "flags any component schemas defined under components.schemas that are not referenced via $ref anywhere in the contract");
    }
}