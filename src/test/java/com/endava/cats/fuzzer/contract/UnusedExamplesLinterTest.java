package com.endava.cats.fuzzer.contract;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class UnusedExamplesLinterTest {
    private TestCaseListener testCaseListener;
    private CatsGlobalContext catsGlobalContext;
    private UnusedExamplesLinter unusedExamplesLinter;

    @BeforeEach
    void setup() {
        catsGlobalContext = Mockito.mock(CatsGlobalContext.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        unusedExamplesLinter = new UnusedExamplesLinter(testCaseListener, catsGlobalContext);
    }

    @Test
    void shouldReportWarnWhenUnusedExamplesExist() {
        Map<String, Example> examples = new HashMap<>();
        examples.put("myExample", new Example());
        Components components = new Components();
        components.setExamples(examples);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();

        unusedExamplesLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Unused component examples found"),
                Mockito.contains("- myExample")
        );
    }

    @Test
    void shouldReportInfoWhenNoExamplesDefined() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        unusedExamplesLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("No examples defined")
        );
    }

    @Test
    void shouldReportInfoWhenAllExamplesUsed() {
        Map<String, Example> examples = new HashMap<>();
        examples.put("usedExample", new Example());
        Components components = new Components();
        components.setExamples(examples);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of("usedExample"));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        unusedExamplesLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("All examples in components.examples are used at least once")
        );
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(unusedExamplesLinter).hasToString(unusedExamplesLinter.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(unusedExamplesLinter.description()).isEqualTo(
                "flags any component examples defined under components.examples that are not referenced via $ref anywhere in the contract");
    }
}

