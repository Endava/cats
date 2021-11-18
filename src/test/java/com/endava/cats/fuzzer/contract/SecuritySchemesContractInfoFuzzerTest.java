package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.HttpMethod;
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
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

@ExtendWith(SpringExtension.class)
class SecuritySchemesContractInfoFuzzerTest {
    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private FilterArguments filterArguments;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    private SecuritySchemesContractInfoFuzzer securitySchemesContractInfoFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        securitySchemesContractInfoFuzzer = new SecuritySchemesContractInfoFuzzer(testCaseListener);
    }

    @Test
    void shouldNotReportAnyError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/openapi.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("pet")).method(HttpMethod.POST).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));
    }

    @Test
    void shouldReportError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-no-security.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").tags(Collections.singletonList("pet")).method(HttpMethod.PUT).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("The current path does not have security scheme(s) defined and there are none defined globally"));
    }

    @Test
    void shouldNotReportErrorWithSecurityAtPathLevel() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-no-path-tags.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").method(HttpMethod.PUT).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));
    }

    @Test
    void shouldNotReportErrorWithSecurityGlobal() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-path-tags-mismatch.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").method(HttpMethod.PUT).tags(Collections.singletonList("petsCats")).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));
    }

    @Test
    void shouldReportWarningWithSecurityGlobalAndSchemeNotDefined() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/contract-security-mismatch-schemes.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").method(HttpMethod.PUT).tags(Collections.singletonList("petsCats")).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportWarn(Mockito.any(), Mockito.eq("The current path has security scheme(s) defined, but they are not present in the [components->securitySchemes] contract element"));
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/openapi.yml"))), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).path("/pet").method(HttpMethod.POST).tags(Collections.singletonList("pet")).pathItem(openAPI.getPaths().get("/pet")).build();
        securitySchemesContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));

        Mockito.reset(testCaseListener);
        securitySchemesContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).reportInfo(Mockito.any(), Mockito.eq("The current path has security scheme(s) properly defined"));
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