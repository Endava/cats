package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

@ExtendWith(SpringExtension.class)
class PathTagsContractInfoFuzzerTest {

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private IgnoreArguments ignoreArguments;

    @MockBean
    private TestCaseExporter testCaseExporter;

    private PathTagsContractInfoFuzzer pathTagsContractInfoFuzzer;

    @BeforeEach
    void setup() {
        pathTagsContractInfoFuzzer = new PathTagsContractInfoFuzzer(testCaseListener);
    }

    @Test
    void shouldNotReportAnyError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/openapi.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("pet")).build();
        pathTagsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.contains("The current path's [tags] are correctly defined at the top level [tags] element"));

    }

    @Test
    void shouldReportErrorWhenMissingPathTags() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-no-path-tags.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").build();
        pathTagsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.contains("The current path does not contain any [tags] element"));
    }

    @Test
    void shouldReportErrorWhenTagsMismatch() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-path-tags-mismatch.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("petsCats")).build();
        pathTagsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.contains("The following [tags] are not present in the top level [tags] element: {}"), Mockito.eq(data.getTags()));
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/openapi.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("pet")).build();
        pathTagsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("The current path's [tags] are correctly defined at the top level [tags] element"));

        Mockito.reset(testCaseListener);
        pathTagsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportInfo(Mockito.any(), Mockito.eq("The current path's [tags] are correctly defined in the top level [tags] element"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(pathTagsContractInfoFuzzer).hasToString(pathTagsContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(pathTagsContractInfoFuzzer.description()).isEqualTo("verifies that all OpenAPI paths contain tags elements and checks if the tags elements match the ones declared at the top level");
    }
}
