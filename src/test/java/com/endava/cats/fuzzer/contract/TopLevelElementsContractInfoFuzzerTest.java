package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseExporterHtmlJs;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import jakarta.enterprise.inject.Instance;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

@QuarkusTest
class TopLevelElementsContractInfoFuzzerTest {

    private TestCaseListener testCaseListener;

    private TopLevelElementsContractInfoFuzzer topLevelElementsContractInfoFuzzer;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        TestCaseExporter exporter = Mockito.mock(TestCaseExporterHtmlJs.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(exporter));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        topLevelElementsContractInfoFuzzer = new TopLevelElementsContractInfoFuzzer(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"src/test/resources/contract-missing-info.yml,info.version, info.contact.url, info.description, info.contact.name, info.title, info.contact.email",
            "src/test/resources/contract-missing-servers.yml,servers",
            "src/test/resources/contract-missing-tags.yml,tags",
            "src/test/resources/contract-incomplete-contact.yml,info.contact.url, info.contact.email",
            "src/test/resources/contract-incomplete-tags.yml,tags,description"})
    void shouldReportError(String contractPath, String expectedError) throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get(contractPath)), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.contains(expectedError));
    }

    @Test
    void shouldNotReportAnyError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/openapi.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.contains("OpenAPI contract contains all top level relevant information!"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(topLevelElementsContractInfoFuzzer).hasToString(topLevelElementsContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(topLevelElementsContractInfoFuzzer.description()).isEqualTo("verifies that all OpenAPI contract level elements are present and provide meaningful information: API description, documentation, title, version, etc. ");
    }
}
