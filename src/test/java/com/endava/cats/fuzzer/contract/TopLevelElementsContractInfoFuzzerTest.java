package com.endava.cats.fuzzer.contract;

import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void shouldReportMissingInfo() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-missing-info.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.contains("info.version, info.contact.url, info.description, info.contact.name, info.title, info.contact.email"));
    }

    @Test
    void shouldReportMissingServers() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-missing-servers.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.contains("servers"));
    }

    @Test
    void shouldReportMissingTags() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-missing-tags.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.contains("tags"));
    }

    @Test
    void shouldReportIncompleteContact() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-incomplete-contact.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.contains("info.contact.url, info.contact.email"));
    }


    @Test
    void shouldNotReportAnyError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/openapi.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        topLevelElementsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.contains("OpenAPI contract contains all top level relevant information!"));
    }
}
