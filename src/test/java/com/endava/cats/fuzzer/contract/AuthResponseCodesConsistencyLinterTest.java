package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@QuarkusTest
class AuthResponseCodesConsistencyLinterTest {
    private TestCaseListener testCaseListener;
    private AuthResponseCodesConsistencyLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new AuthResponseCodesConsistencyLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenSecuredOperationDocuments401() {
        OpenAPI openAPI = new OpenAPI().security(List.of(new SecurityRequirement().addList("bearerAuth")));
        PathItem pathItem = new PathItem().get(new Operation());

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .path("/invoices")
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .responseCodes(Set.of("200", "401", "500"))
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Authentication response codes (401/403) are consistent with the operation's security requirements")
        );
    }

    @Test
    void shouldReportWarnWhenSecuredOperationMisses401And403() {
        OpenAPI openAPI = new OpenAPI().security(List.of(new SecurityRequirement().addList("bearerAuth")));
        PathItem pathItem = new PathItem().get(new Operation());

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .path("/invoices")
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .responseCodes(Set.of("200", "500"))
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Secured operation missing 401/403 response"),
                Mockito.contains("does not document 401 or 403 response codes")
        );
    }

    @Test
    void shouldReportWarnWhenExplicitlyPublicOperationDocuments401() {
        OpenAPI openAPI = new OpenAPI().security(List.of(new SecurityRequirement().addList("bearerAuth")));
        Operation publicOp = new Operation().security(Collections.emptyList());
        PathItem pathItem = new PathItem().get(publicOp);

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .path("/public-catalog")
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .responseCodes(Set.of("200", "401"))
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Public operation documents 401 response"),
                Mockito.contains("explicitly opts out of authentication (security: [])")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("AuthResponseCodesConsistencyLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that secured operations document 401/403 error responses and public operations opting out of security (security: []) do not document 401");
    }
}
