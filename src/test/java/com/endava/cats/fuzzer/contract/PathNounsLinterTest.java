package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PathNounsLinterTest {
    private TestCaseListener listener;
    private PathNounsLinter linter;

    @BeforeEach
    void setup() {
        listener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new PathNounsLinter(listener);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /users",
            "POST, /reports",
            "GET, /reports/{reportId}",
            "GET, /reports/{reportId}/executions",
            "POST, /reports/schedule",
            "POST, /reports/batch",
            "POST, /reports/{reportId}/cancel",
            "POST, /tickets/{ticketId}/feedback/submit",
            "GET, /subscriptions/validate",
            "POST, /subscriptions/validate",
            "GET, /reports/name-check",
            "GET, /branding/resolved-configuration",
            "GET, /branding/effective-configuration",
            "GET, /branding/default-configuration",
            "GET, /tickets/{ticketId}/evidence/uploads",
            "POST, /tickets/{ticketId}/evidence/uploads",
            "GET, /admin/permissions",
            "POST, /admin/caches/rebuild",
            "GET, /api/internal/v1/reports",
            "GET, /users/current-user",
            "GET, /users/me",
            "GET, /users/self/roles",
            "GET, /subscriptions/current-subscription",
            "GET, /reports/{reportId}/download-token",
            "GET, /reports/{reportId}/downloadToken",
            "GET, /reports/{reportId}/download_token",
            "GET, /reports/{reportId}/download-tokens",
            "GET, /assessments/summary",
            "GET, /people",
            "GET, /children",
            "GET, /reports/{reportId}/csv",
            "GET, /healthcheck",
            "PATCH, /tickets/{ticketId}/reference-link",
            "PATCH, /tickets/{ticketId}/document-link",
            "GET, /api/customers/{id}/shipping-address",
            "POST, /orders/{orderId}/delivery-attempt-result",
            "POST, /orders/{orderId}/inspection-method-result",
            "GET, /patch-notes",
            "GET, /posts/{postId}/post-comments",
            "POST, /credentials/{id}/revoke"
    })
    void shouldAcceptResourceGrammar(HttpMethod method, String path) {
        linter.fuzz(data(method, path));
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("Path segments use resource nouns and well-formed custom operations."));
        Mockito.verify(listener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /organizations/resolve/{externalOrgId}, resolve",
            "POST, /reports/schedule/{scheduleId}, schedule",
            "GET, /branding/resolved, resolved",
            "GET, /branding/configuration-resolved, configuration-resolved",
            "GET, /branding/effective, effective",
            "GET, /reports/{id}/cancel, cancel",
            "PUT, /reports/{id}/cancel, cancel",
            "DELETE, /reports/{id}/cancel, cancel",
            "POST, /reports/create-token, create-token",
            "POST, /reports/get-report, get-report",
            "POST, /create-report, create-report",
            "POST, /reports/update-report, update-report",
            "GET, /findById, findById",
            "POST, /addUser, addUser",
            "POST, /executeTask, executeTask",
            "POST, /reports/cancel/executions, cancel"
    })
    void shouldRejectNamingViolations(HttpMethod method, String path, String invalid) {
        linter.fuzz(data(method, path));
        Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.any(),
                Mockito.eq("Path resource or operation naming violations"),
                Mockito.eq("The following path segments violate resource or operation naming rules: {}"),
                Mockito.contains(invalid));
    }

    @Test
    void shouldRunOnlyOnceForTheSamePathAndMethod() {
        FuzzingData data = data(HttpMethod.GET, "/reports");
        linter.fuzz(data);
        linter.fuzz(data);
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void shouldDescribeTheLinter() {
        assertThat(linter.toString()).isEqualTo("PathNounsLinter");
        assertThat(linter.description()).isEqualTo("verifies that path segments use resource nouns and that custom operations "
                + "are verb-only, terminal, nested under a resource, and invoked with a matching HTTP method");
    }

    private static FuzzingData data(HttpMethod method, String path) {
        PathItem pathItem = new PathItem();
        pathItem.operation(PathItem.HttpMethod.valueOf(method.name()), new Operation().responses(new ApiResponses()));
        return FuzzingData.builder().path(path).method(method).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats", new Schema().$ref("Cats"))).pathItem(pathItem)
                .headers(Set.of()).reqSchemaName("Cats").build();
    }

    @Test
    void shouldEvaluateDifferentMethodsIndependently() {
        linter.fuzz(data(HttpMethod.POST, "/reports/{id}/cancel"));
        linter.fuzz(data(HttpMethod.GET, "/reports/{id}/cancel"));
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString());
        Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.contains("cancel"));
    }
}
