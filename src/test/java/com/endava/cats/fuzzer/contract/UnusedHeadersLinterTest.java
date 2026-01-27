package com.endava.cats.fuzzer.contract;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class UnusedHeadersLinterTest {
    private TestCaseListener testCaseListener;
    private CatsGlobalContext catsGlobalContext;
    private UnusedHeadersLinter unusedHeadersLinter;

    @BeforeEach
    void setup() {
        catsGlobalContext = Mockito.mock(CatsGlobalContext.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        unusedHeadersLinter = new UnusedHeadersLinter(testCaseListener, catsGlobalContext);
    }

    @Test
    void shouldReportWarnWhenUnusedHeadersExist() {
        Map<String, Header> headers = new HashMap<>();
        headers.put("X-My-Header", new Header());
        Components components = new Components();
        components.setHeaders(headers);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();

        unusedHeadersLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Unused component headers found"),
                Mockito.contains("- X-My-Header")
        );
    }

    @Test
    void shouldReportInfoWhenNoHeadersDefined() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of());
        unusedHeadersLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("No headers defined")
        );
    }

    @Test
    void shouldReportInfoWhenAllHeadersUsed() {
        Map<String, Header> headers = new HashMap<>();
        headers.put("X-Used-Header", new Header());
        Components components = new Components();
        components.setHeaders(headers);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);
        Mockito.when(catsGlobalContext.getRefs()).thenReturn(Set.of("X-Used-Header"));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        unusedHeadersLinter.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.contains("All headers in components.headers are used at least once")
        );
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(unusedHeadersLinter).hasToString(unusedHeadersLinter.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(unusedHeadersLinter.description()).isEqualTo(
                "flags any component headers defined under components.headers that are not referenced via $ref anywhere in the contract");
    }
}

