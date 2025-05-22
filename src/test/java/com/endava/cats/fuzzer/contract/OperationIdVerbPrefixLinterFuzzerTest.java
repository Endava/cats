package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseExporterHtmlJs;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.stream.Stream;

@QuarkusTest
class OperationIdVerbPrefixLinterFuzzerTest {
    private TestCaseListener testCaseListener;
    private OperationIdVerbPrefixLinterFuzzer operationIdVerbPrefixLinterFuzzer;

    @Inject
    NamingArguments namingArguments;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        TestCaseExporter exporter = Mockito.mock(TestCaseExporterHtmlJs.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(exporter));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        operationIdVerbPrefixLinterFuzzer = new OperationIdVerbPrefixLinterFuzzer(testCaseListener, namingArguments);
        namingArguments.loadVerbMapFile();
    }

    @ParameterizedTest
    @CsvSource(value = {"null", "createPeople", "deletePeople"}, nullValues = "null")
    void shouldReportError(String operationId) {
        PathItem pathItem = Mockito.mock(PathItem.class);
        Operation operation = new Operation();
        operation.setOperationId(operationId);
        Mockito.when(pathItem.getGet()).thenReturn(operation);

        FuzzingData data = FuzzingData.builder().pathItem(pathItem).method(HttpMethod.GET).build();
        operationIdVerbPrefixLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("OperationId prefix mismatch"),
                Mockito.eq("OperationId [{}] does not start with any allowed prefix {}"), Mockito.eq(operationId), Mockito.any());
    }

    @Test
    void shouldReportInfo() {
        PathItem pathItem = Mockito.mock(PathItem.class);
        Operation operation = new Operation();
        operation.setOperationId("getPeople");
        Mockito.when(pathItem.getGet()).thenReturn(operation);

        FuzzingData data = FuzzingData.builder().pathItem(pathItem).method(HttpMethod.GET).build();
        operationIdVerbPrefixLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("OperationId [{}] uses an allowed prefix"), Mockito.eq("getPeople"));
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
