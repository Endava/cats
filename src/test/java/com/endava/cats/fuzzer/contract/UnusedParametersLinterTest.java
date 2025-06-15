package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class UnusedParametersLinterTest {
    private TestCaseListener testCaseListener;
    private CatsGlobalContext catsGlobalContext;
    private UnusedParametersLinter unusedParametersLinter;

    @BeforeEach
    void setup() {
        catsGlobalContext = Mockito.mock(CatsGlobalContext.class);
        testCaseListener = Mockito.spy(new TestCaseListener(catsGlobalContext, Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        unusedParametersLinter = new UnusedParametersLinter(testCaseListener, catsGlobalContext);
    }

    @Test
    void shouldReportWarnWhenUnusedParametersExist() {
        Map<String, Parameter> parameters = new HashMap<>();
        parameters.put("myParam", new Parameter());
        Components components = new Components();
        components.setParameters(parameters);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();

        unusedParametersLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Unused component parameters found"),
                Mockito.contains("- myParam")
        );
    }

    @Test
    void shouldReportInfoWhenNoParametersDefined() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        unusedParametersLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("No parameters defined")
        );
    }

    @Test
    void shouldReportInfoWhenAllParametersUsed() {
        Map<String, Parameter> parameters = new HashMap<>();
        parameters.put("usedParam", new Parameter());
        Components components = new Components();
        components.setParameters(parameters);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of("usedParam"));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        unusedParametersLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("All parameters in components.parameters are used at least once")
        );
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(unusedParametersLinter).hasToString(unusedParametersLinter.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(unusedParametersLinter.description()).isEqualTo(
                "flags any component parameters defined under components.parameters that are not referenced via $ref anywhere in the contract");
    }
}