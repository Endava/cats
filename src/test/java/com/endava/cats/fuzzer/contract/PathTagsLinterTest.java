package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

@QuarkusTest
class PathTagsLinterTest {

    private TestCaseListener testCaseListener;
    private PathTagsLinter pathTagsContractInfoFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        pathTagsContractInfoFuzzer = new PathTagsLinter(testCaseListener);
    }

    @Test
    void shouldNotReportAnyError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/openapi.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("pet")).build();
        pathTagsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.contains("The current path's [tags] are correctly defined at the top level [tags] element"));

    }

    @Test
    void shouldReportErrorWhenMissingPathTags() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/contract-no-path-tags.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").build();
        pathTagsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.contains("The current path does not contain any [tags] element"));
    }

    @Test
    void shouldReportErrorWhenTagsMismatch() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/contract-path-tags-mismatch.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("petsCats")).build();
        pathTagsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.contains("The following [tags] are not present in the top level [tags] element: {}"), Mockito.eq(data.getTags()));
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/openapi.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("pet")).build();
        pathTagsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("The current path's [tags] are correctly defined at the top level [tags] element"));

        Mockito.reset(testCaseListener);
        pathTagsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("The current path's [tags] are correctly defined in the top level [tags] element"));
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
