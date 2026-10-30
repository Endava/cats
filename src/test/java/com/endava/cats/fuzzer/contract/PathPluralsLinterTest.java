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
class PathPluralsLinterTest {
    private TestCaseListener listener;
    private PathPluralsLinter linter;

    @BeforeEach
    void setup() {
        listener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new PathPluralsLinter(listener);
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
            "GET, /tickets/{ticketId}/audit"
    })
    void shouldAcceptResourceGrammar(HttpMethod method, String path) {
        linter.fuzz(data(method, path));
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString());
        Mockito.verify(listener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource({
            "DELETE, /carts/{cartId}/token/{token}, token",
            "POST, /carts/{cartId}/token/{token}, token",
            "GET, /reports/{reportId}/execution/{executionId}, execution",
            "GET, /report/{reportId}/executions, report",
            "POST, /report/{reportId}/cancel, report",
            "POST, /reports/report, report",
            "POST, /api/report, report",
            "GET, /reports/admin, admin",
            "GET, /v1/petsPath, petsPath",
            "GET, /user, user",
            "GET, /reports/{id}/status/{statusId}, status",
            "GET, /audit/{auditId}, audit"
    })
    void shouldRejectNamingViolations(HttpMethod method, String path, String invalid) {
        linter.fuzz(data(method, path));
        Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.any(),
                Mockito.eq("Path elements not plural"),
                Mockito.eq("Some of the following path elements are not using pluralization: {}"),
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
        assertThat(linter.toString()).isEqualTo("PathPluralsLinter");
        assertThat(linter.description()).isEqualTo("verifies that collection segments use plural nouns and flags singular "
                + "or verb-prefixed names whose role is uncertain");
    }

    private static FuzzingData data(HttpMethod method, String path) {
        PathItem pathItem = new PathItem();
        pathItem.operation(PathItem.HttpMethod.valueOf(method.name()), new Operation().responses(new ApiResponses()));
        return FuzzingData.builder().path(path).method(method).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats", new Schema().$ref("Cats"))).pathItem(pathItem)
                .headers(Set.of()).reqSchemaName("Cats").build();
    }

    @ParameterizedTest
    @CsvSource({"GET, /organizations/resolve/{externalOrgId}", "GET, /branding/resolved", "GET, /reports/{id}/cancel"})
    void shouldLeaveOperationAndNounErrorsToTheNounLinter(HttpMethod method, String path) {
        linter.fuzz(data(method, path));
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString());
        Mockito.verify(listener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource({"GET, /reports/{reportId}/tenant-widget", "POST, /reports/frobnicate"})
    void shouldWarnAboutUnrecognizedSingularRoles(HttpMethod method, String path) {
        linter.fuzz(data(method, path));
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.eq("Uncertain path naming roles"), Mockito.anyString(), Mockito.any());
    }
}
