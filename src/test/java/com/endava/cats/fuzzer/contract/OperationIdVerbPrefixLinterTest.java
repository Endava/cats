package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.NamingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.List;

@QuarkusTest
class OperationIdVerbPrefixLinterTest {
    private TestCaseListener testCaseListener;
    private OperationIdVerbPrefixLinter operationIdVerbPrefixLinterFuzzer;

    @Inject
    NamingArguments namingArguments;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        operationIdVerbPrefixLinterFuzzer = new OperationIdVerbPrefixLinter(testCaseListener, namingArguments);
        ReflectionTestUtils.setField(namingArguments, "operationPrefixMapFile", null);
        namingArguments.loadVerbMapFile();
    }

    @ParameterizedTest
    @CsvSource(value = {"null", "createPeople", "deletePeople"}, nullValues = "null")
    void shouldReportError(String operationId) {
        PathItem pathItem = Mockito.mock(PathItem.class);
        Operation operation = new Operation();
        operation.setOperationId(operationId);
        Mockito.when(pathItem.getGet()).thenReturn(operation);

        FuzzingData data = FuzzingData.builder().pathItem(pathItem).path("/test").method(HttpMethod.GET).build();
        operationIdVerbPrefixLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("OperationId prefix mismatch"),
                Mockito.eq("OperationId [{}] does not start with any allowed prefix {}"), Mockito.eq(operationId), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource(value = {"getPeople,/test", "compressPeople,/compress", "cancel,/cancel-tokens"})
    void shouldReportInfo(String operationId, String path) {
        PathItem pathItem = Mockito.mock(PathItem.class);
        Operation operation = new Operation();
        operation.setOperationId(operationId);
        Mockito.when(pathItem.getGet()).thenReturn(operation);

        FuzzingData data = FuzzingData.builder().pathItem(pathItem).path(path).method(HttpMethod.GET).build();
        operationIdVerbPrefixLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("OperationId [{}] uses an allowed prefix"), Mockito.eq(operationId));
    }

    @Test
    void shouldReportErrorOnCustomVerbFile() {
        ReflectionTestUtils.setField(namingArguments, "operationPrefixMapFile", new File("src/test/resources/verbs.properties"));
        namingArguments.loadVerbMapFile();
        PathItem pathItem = Mockito.mock(PathItem.class);
        Operation operation = new Operation();
        operation.setOperationId("getPeople");
        Mockito.when(pathItem.getGet()).thenReturn(operation);

        FuzzingData data = FuzzingData.builder().pathItem(pathItem).path("/test").method(HttpMethod.GET).build();
        operationIdVerbPrefixLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("OperationId prefix mismatch"),
                Mockito.eq("OperationId [{}] does not start with any allowed prefix {}"), Mockito.eq("getPeople"), Mockito.eq(List.of("fail")));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(operationIdVerbPrefixLinterFuzzer).hasToString(operationIdVerbPrefixLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(operationIdVerbPrefixLinterFuzzer.description()).isEqualTo("verifies that each operationId starts with one of the allowed prefixes per HTTP method, based on configuration");
    }
}
