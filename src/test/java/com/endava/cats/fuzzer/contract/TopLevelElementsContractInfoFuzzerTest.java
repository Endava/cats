package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Paths;

@ExtendWith(SpringExtension.class)
class TopLevelElementsContractInfoFuzzerTest {

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private IgnoreArguments ignoreArguments;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    private TopLevelElementsContractInfoFuzzer topLevelElementsContractInfoFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        topLevelElementsContractInfoFuzzer = new TopLevelElementsContractInfoFuzzer(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"src/test/resources/contract-missing-info.yml,info.version, info.contact.url, info.description, info.contact.name, info.title, info.contact.email",
            "src/test/resources/contract-missing-servers.yml,servers",
            "src/test/resources/contract-missing-tags.yml,tags",
            "src/test/resources/contract-incomplete-contact.yml,info.contact.url, info.contact.email",
            "src/test/resources/contract-incomplete-tags.yml,tags,description"})
    void shouldReportError(String contractPath, String expectedError) throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get(contractPath))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.contains(expectedError));
    }

    @Test
    void shouldNotReportAnyError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/openapi.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.contains("OpenAPI contract contains all top level relevant information!"));
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
