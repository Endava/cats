package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

@QuarkusTest
class PathParameterDeclarationLinterTest {
    private TestCaseListener testCaseListener;
    private PathParameterDeclarationLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new PathParameterDeclarationLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenPathParametersMatchUrlVariables() {
        Operation getOp = new Operation().parameters(List.of(
                new Parameter().name("userId").in("path").required(true)
        ));
        PathItem pathItem = new PathItem().get(getOp);

        FuzzingData data = FuzzingData.builder()
                .openApi(new OpenAPI())
                .path("/users/{userId}")
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All path parameters are properly declared and aligned with the URL template")
        );
    }

    @Test
    void shouldReportErrorWhenUrlVariableUndeclaredOrOrphanOrNotRequired() {
        Operation getOp = new Operation().parameters(List.of(
                new Parameter().name("orphanId").in("path").required(false)
        ));
        PathItem pathItem = new PathItem().get(getOp);

        FuzzingData data = FuzzingData.builder()
                .openApi(new OpenAPI())
                .path("/users/{userId}")
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Invalid path parameter declarations"),
                Mockito.argThat((String msg) -> msg.contains("Path variable '{userId}' in URL is not declared")
                        && msg.contains("Declared path parameter 'orphanId' is not present")
                        && msg.contains("Path parameter 'orphanId' must specify 'required: true'"))
        );
    }

    @Test
    void shouldReturnSimpleClassNameAndDescription() {
        Assertions.assertThat(linter).hasToString("PathParameterDeclarationLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that URL path template variables ({param}) match declared 'in: path' parameters and have 'required: true'");
    }
}
