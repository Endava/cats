package com.endava.cats.fuzzer.contract;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class UnusedRequestBodiesLinterTest {
    private TestCaseListener testCaseListener;
    private CatsGlobalContext catsGlobalContext;
    private UnusedRequestBodiesLinter unusedRequestBodiesLinter;

    @BeforeEach
    void setup() {
        catsGlobalContext = Mockito.mock(CatsGlobalContext.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        unusedRequestBodiesLinter = new UnusedRequestBodiesLinter(testCaseListener, catsGlobalContext);
    }

    @Test
    void shouldReportWarnWhenUnusedRequestBodiesExist() {
        Map<String, RequestBody> requestBodies = new HashMap<>();
        requestBodies.put("myRequest", new RequestBody());
        Components components = new Components();
        components.setRequestBodies(requestBodies);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();

        unusedRequestBodiesLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Unused component requestBodies found"),
                Mockito.contains("- myRequest")
        );
    }

    @Test
    void shouldReportInfoWhenNoRequestBodiesDefined() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        unusedRequestBodiesLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("No requestBodies defined")
        );
    }

    @Test
    void shouldReportInfoWhenAllRequestBodiesUsed() {
        Map<String, RequestBody> requestBodies = new HashMap<>();
        requestBodies.put("usedBody", new RequestBody());
        Components components = new Components();
        components.setRequestBodies(requestBodies);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of("usedBody"));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        unusedRequestBodiesLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("All requestBodies in components.requestBodies are used at least once")
        );
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(unusedRequestBodiesLinter).hasToString(unusedRequestBodiesLinter.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(unusedRequestBodiesLinter.description()).isEqualTo(
                "flags any component requestBodies defined under components.requestBodies that are not referenced via $ref anywhere in the contract");
    }
}