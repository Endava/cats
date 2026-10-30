package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.contract.util.HttpMethodConsistencyAnalyzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

@QuarkusTest
class HttpMethodConsistencyLinterTest {
    private TestCaseListener testCaseListener;
    private HttpMethodConsistencyLinter httpMethodConsistencyLinter;
    @Inject
    HttpMethodConsistencyAnalyzer httpMethodConsistencyAnalyzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        httpMethodConsistencyLinter = new HttpMethodConsistencyLinter(testCaseListener, httpMethodConsistencyAnalyzer);
    }

    @Test
    void shouldReportWarnWhenMethodsAreMissing() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Path.of("src/test/resources/inconsistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations-bad/{id}")).build();
        httpMethodConsistencyLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(),
                Mockito.eq("HTTP method consistency issues"),
                Mockito.eq("The following HTTP method consistency issues were found:\n- Group [/pets] missing POST on collection path [/pets]; missing DELETE on item path [/pets/{id}]; missing at least one of PUT or PATCH on item path [/pets/{id}]\n"));
    }

    @Test
    void shouldReportInfoWhenAllMethodsPresent() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Path.of("src/test/resources/consistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations/{id}")).reqSchema(new Schema()).build();
        httpMethodConsistencyLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("All resource groups have consistent REST HTTP methods"));
    }

    @Test
    void shouldNotFlagStandaloneReadOnlyCollectionWithoutItemPath() {
        OpenAPI openAPI = new OpenAPI();
        Paths paths = new Paths();
        paths.addPathItem("/catalogs", new PathItem().get(new Operation()));
        openAPI.setPaths(paths);

        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(paths.get("/catalogs")).build();
        httpMethodConsistencyLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("All resource groups have consistent REST HTTP methods"));
    }

    @Test
    void shouldPreserveUncountableCollectionPathsInMethodConsistencyCheck() {
        OpenAPI openAPI = new OpenAPI();
        Paths paths = new Paths();
        paths.addPathItem("/evidence", new PathItem().post(new Operation()).get(new Operation()));
        paths.addPathItem("/evidence/{evidenceId}", new PathItem()
                .get(new Operation())
                .put(new Operation())
                .delete(new Operation()));
        openAPI.setPaths(paths);

        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(paths.get("/evidence")).build();
        httpMethodConsistencyLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("All resource groups have consistent REST HTTP methods"));
    }

    @Test
    void shouldPreserveVerbPrefixedCompoundCollectionsInMethodConsistencyCheck() {
        OpenAPI openAPI = new OpenAPI();
        Paths paths = new Paths();
        paths.addPathItem("/refund-requests", new PathItem().post(new Operation()).get(new Operation()));
        paths.addPathItem("/refund-requests/{id}", new PathItem()
                .get(new Operation())
                .put(new Operation())
                .delete(new Operation()));
        openAPI.setPaths(paths);

        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(paths.get("/refund-requests")).build();
        httpMethodConsistencyLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("All resource groups have consistent REST HTTP methods"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(httpMethodConsistencyLinter).hasToString(httpMethodConsistencyLinter.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(httpMethodConsistencyLinter.description())
                .isEqualTo("flags missing REST HTTP methods (POST on collection paths and GET, PUT/PATCH, DELETE on item paths) across resource groups");
    }
}
