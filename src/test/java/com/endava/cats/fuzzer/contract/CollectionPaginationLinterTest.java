package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;

@QuarkusTest
class CollectionPaginationLinterTest {
    private TestCaseListener testCaseListener;
    private CollectionPaginationLinter collectionPaginationLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        collectionPaginationLinterFuzzer = new CollectionPaginationLinter(testCaseListener);
    }

    @Test
    void shouldReportWarn() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore31.yaml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pet/findByTags")).method(HttpMethod.GET).path("/pet/findByTags").build();
        collectionPaginationLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.eq("Missing pagination support on GET collection endpoints"),
                Mockito.contains("Operation is missing pagination query parameters"));
    }

    @Test
    void shouldSkipOnItemPath() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations/{id}")).path("/pets/operations/{id}")
                .method(HttpMethod.GET).reqSchema(new Schema()).build();
        collectionPaginationLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.eq("Skipping pagination check for non-GET operations"));
    }

    @Test
    void shouldSkipOnNonGetMethod() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pet/findByTags")).path("/pet/findByTags")
                .method(HttpMethod.POST).reqSchema(new Schema()).build();
        collectionPaginationLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.eq("Skipping pagination check for non-GET operations"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(collectionPaginationLinterFuzzer).hasToString(collectionPaginationLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(collectionPaginationLinterFuzzer.description()).isEqualTo("verifies that all GET operations on collection endpoints support pagination via query parameters like limit, offset, page, or cursor");
    }
}
