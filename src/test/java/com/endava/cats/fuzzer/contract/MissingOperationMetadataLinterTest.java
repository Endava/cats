package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class MissingOperationMetadataLinterTest {
    private TestCaseListener testCaseListener;
    private MissingOperationMetadataLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new MissingOperationMetadataLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenOperationHasOperationIdAndSummary() {
        Operation getOp = new Operation().operationId("listUsers").summary("Lists all users");
        PathItem pathItem = new PathItem().get(getOp);

        FuzzingData data = FuzzingData.builder()
                .path("/users")
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Operation defines operationId and summary/description metadata")
        );
    }

    @Test
    void shouldReportWarnWhenOperationIdAndSummaryAndDescriptionMissing() {
        Operation getOp = new Operation();
        PathItem pathItem = new PathItem().get(getOp);

        FuzzingData data = FuzzingData.builder()
                .path("/users")
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Missing operation metadata"),
                Mockito.eq("Operation is missing 'operationId'; Operation is missing both 'summary' and 'description'")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("MissingOperationMetadataLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that every operation defines a non-blank operationId and at least one of summary or description");
    }
}
