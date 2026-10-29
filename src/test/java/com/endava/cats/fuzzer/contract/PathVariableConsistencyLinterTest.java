package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

@QuarkusTest
class PathVariableConsistencyLinterTest {
    private TestCaseListener testCaseListener;
    private PathVariableConsistencyLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new PathVariableConsistencyLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenPathVariablesAreConsistent() {
        Parameter getId = new Parameter().name("accountId").in("path").schema(new Schema<>().type("string").format("uuid"));
        Parameter deleteId = new Parameter().name("accountId").in("path").schema(new Schema<>().type("string").format("uuid"));

        OpenAPI openAPI = new OpenAPI().paths(new Paths()
                .addPathItem("/accounts/{accountId}", new PathItem()
                        .get(new Operation().parameters(List.of(getId)))
                        .delete(new Operation().parameters(List.of(deleteId))))
                .addPathItem("/accounts/{accountId}/books", new PathItem()
                        .get(new Operation().parameters(List.of(getId)))));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All path variables use consistent names and schema types/formats across the contract")
        );
    }

    @Test
    void shouldReportWarnWhenSegmentUsesDifferentVariableNamesOrTypes() {
        Parameter uuidParam = new Parameter().name("accountId").in("path").schema(new Schema<>().type("string").format("uuid"));
        Parameter intParam = new Parameter().name("accountId").in("path").schema(new Schema<>().type("integer").format("int64"));
        Parameter idParam = new Parameter().name("id").in("path").schema(new Schema<>().type("string").format("uuid"));

        OpenAPI openAPI = new OpenAPI().paths(new Paths()
                .addPathItem("/accounts/{accountId}", new PathItem()
                        .get(new Operation().parameters(List.of(uuidParam)))
                        .delete(new Operation().parameters(List.of(intParam))))
                .addPathItem("/accounts/{id}/books", new PathItem()
                        .get(new Operation().parameters(List.of(idParam)))));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Inconsistent path variable definitions found"),
                Mockito.argThat((String msg) -> msg.contains("Resource segment '/accounts' uses inconsistent path variable names across paths: [accountId, id]")
                        && msg.contains("Path parameter '{accountId}' is declared with inconsistent type/format across operations: [string:uuid, integer:int64]"))
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("PathVariableConsistencyLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that path variables use consistent names per resource segment and consistent schema types/formats across all operations");
    }
}
