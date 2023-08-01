package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.http.HttpMethod;
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
import org.mockito.Mockito;

import jakarta.enterprise.inject.Instance;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.stream.Stream;

@QuarkusTest
class SecuritySchemesLinterFuzzerTest {
    private TestCaseListener testCaseListener;

    private SecuritySchemesLinterFuzzer securitySchemesContractInfoFuzzer;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        TestCaseExporter exporter = Mockito.mock(TestCaseExporterHtmlJs.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(exporter));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        securitySchemesContractInfoFuzzer = new SecuritySchemesLinterFuzzer(testCaseListener);
    }

    @Test
    void shouldNotReportAnyError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/openapi.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("pet")).method(HttpMethod.POST).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));
    }

    @Test
    void shouldReportError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/contract-no-security.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("pet")).method(HttpMethod.PUT).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.eq("The current path does not have security scheme(s) defined and there are none defined globally"));
    }

    @Test
    void shouldNotReportErrorWithSecurityAtPathLevel() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/contract-no-path-tags.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").method(HttpMethod.PUT).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));
    }

    @Test
    void shouldNotReportErrorWithSecurityGlobal() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/contract-path-tags-mismatch.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").method(HttpMethod.PUT).tags(Collections.singletonList("petsCats")).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));
    }

    @Test
    void shouldReportWarningWithSecurityGlobalAndSchemeNotDefined() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/contract-security-mismatch-schemes.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").method(HttpMethod.PUT).tags(Collections.singletonList("petsCats")).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("The current path has security scheme(s) defined, but they are not present in the [components->securitySchemes] contract element"));
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/openapi.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").method(HttpMethod.POST).tags(Collections.singletonList("pet")).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));

        Mockito.reset(testCaseListener);
        securitySchemesContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(securitySchemesContractInfoFuzzer).hasToString(securitySchemesContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(securitySchemesContractInfoFuzzer.description()).isEqualTo("verifies if the OpenApi contract contains valid security schemas for all paths, either globally configured or per path");
    }
}