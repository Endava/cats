package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;

@QuarkusTest
class VersionsLinterTest {
    private TestCaseListener testCaseListener;
    private VersionsLinter versionsContractInfoFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        versionsContractInfoFuzzer = new VersionsLinter(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"src/test/resources/openapi.yml", "src/test/resources/issue86.json"})
    void shouldReportError(String contractPath) throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get(contractPath)), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("OpenAPI contract contains versioning information"));
    }

    @Test
    void shouldReportInfo() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Versioning information not found"),
                Mockito.eq("OpenAPI contract does not contain versioning information"));
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        versionsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Versioning information not found"),
                Mockito.eq("OpenAPI contract does not contain versioning information"));
        Mockito.reset(testCaseListener);
        versionsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path does not contain versioning information"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(versionsContractInfoFuzzer).hasToString(versionsContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(versionsContractInfoFuzzer.description()).isEqualTo("verifies that a given path doesn't contain versioning information");
    }

}
