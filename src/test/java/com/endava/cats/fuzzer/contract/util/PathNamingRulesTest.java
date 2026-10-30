package com.endava.cats.fuzzer.contract.util;

import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PathNamingRulesTest {
    @ParameterizedTest
    @CsvSource({
            "GET, /users/current-user, IDENTIFIER",
            "GET, /subscriptions/current-subscription, IDENTIFIER",
            "GET, /subscriptions/currentSubscription, IDENTIFIER",
            "GET, /reports/{reportId}, IDENTIFIER",
            "GET, /tickets/{ticketId}/evidence, SINGLETON",
            "GET, /branding/resolved-configuration, SINGLETON",
            "GET, /branding/effectiveConfiguration, SINGLETON",
            "GET, /branding/default_configuration, SINGLETON",
            "GET, /assessments/summary, SINGLETON",
            "GET, /reports/{reportId}/download-token, SINGLETON",
            "GET, /reports/{reportId}/download-tokens, COLLECTION",
            "GET, /search-results, COLLECTION",
            "GET, /purchase-orders, COLLECTION",
            "GET, /process-definitions, COLLECTION",
            "GET, /people, COLLECTION",
            "GET, /children, COLLECTION",
            "GET, /admin/reports, COLLECTION",
            "GET, /subscriptions/validate, OPERATION",
            "HEAD, /subscriptions/validate, OPERATION",
            "POST, /subscriptions/validate, OPERATION",
            "GET, /reports/name-check, OPERATION",
            "POST, /reports/schedule, OPERATION",
            "POST, /admin/caches/rebuild, OPERATION",
            "POST, /reports/batch, OPERATION",
            "PATCH, /tickets/{id}/reference-link, SINGLETON",
            "GET, /customers/{id}/shipping-address, SINGLETON",
            "POST, /orders/{id}/delivery-attempt-result, SINGLETON",
            "GET, /patch-notes, COLLECTION",
            "GET, /posts/{postId}/post-comments, COLLECTION",
            "GET, /tickets/{ticketId}/audit, SINGLETON",
            "POST, /credentials/{id}/revoke, OPERATION",
            "POST, /credentials/{id}/rotate, OPERATION",
            "PATCH, /users/{id}/state, SINGLETON",
            "GET, /areas/{areaId}, IDENTIFIER",
            "GET, /schemas/{schemaId}, IDENTIFIER",
            "GET, /menus/{menuId}, IDENTIFIER",
            "GET, /vcpus/{id}, IDENTIFIER",
            "GET, /pdus/{id}, IDENTIFIER",
            "GET, /tpus/{id}, IDENTIFIER",
            "GET, /mcus/{id}, IDENTIFIER",
            "GET, /plus/{id}, IDENTIFIER",
            "GET, /emus/{id}, IDENTIFIER",
            "GET, /gurus/{id}, IDENTIFIER",
            "GET, /haikus/{id}, IDENTIFIER",
            "GET, /tofus/{id}, IDENTIFIER",
            "GET, /sudokus/{id}, IDENTIFIER",
            "GET, /accelerator-tpus, COLLECTION",
            "GET, /apis/{apiId}, IDENTIFIER",
            "GET, /add-ons, COLLECTION",
            "GET, /addOns, COLLECTION",
            "GET, /add-ons/{id}, IDENTIFIER",
            "GET, /export-jobs/{id}, IDENTIFIER",
            "GET, /restore-points/{id}, IDENTIFIER",
            "GET, /refund-requests, COLLECTION",
            "GET, /stop-words, COLLECTION",
            "GET, /stop-times, COLLECTION",
            "GET, /stop-points/{id}, IDENTIFIER",
            "GET, /start-times, COLLECTION",
            "GET, /start-events, COLLECTION",
            "GET, /reports/{id}/import-result, SINGLETON",
            "GET, /reports/{id}/refund-status, SINGLETON",
            "GET, /reports/{id}/check-result, SINGLETON",
            "GET, /search-indexes, COLLECTION",
            "GET, /search-queries, COLLECTION",
            "GET, /search-filters, COLLECTION",
            "GET, /schedule-slots, COLLECTION",
            "GET, /scheduleSlots, COLLECTION",
            "GET, /schedule-entries, COLLECTION",
            "GET, /scheduleEntries, COLLECTION",
            "GET, /schedule-templates, COLLECTION",
            "GET, /register-entries, COLLECTION",
            "GET, /archive-folders, COLLECTION",
            "GET, /export-settings, COLLECTION",
            "GET, /export-files, COLLECTION",
            "GET, /import-mappings, COLLECTION",
            "GET, /import-batches, COLLECTION",
            "GET, /release-notes, COLLECTION",
            "GET, /release-channels, COLLECTION",
            "GET, /sync-jobs, COLLECTION",
            "GET, /sync-rules, COLLECTION",
            "GET, /merge-requests, COLLECTION",
            "GET, /share-links, COLLECTION",
            "GET, /invite-codes, COLLECTION",
            "GET, /invite-links/{id}, IDENTIFIER",
            "GET, /copy-jobs/{id}, IDENTIFIER",
            "GET, /lock-files, COLLECTION",
            "GET, /transfer-requests, COLLECTION",
            "GET, /claim-forms, COLLECTION",
            "GET, /openapi.json, SINGLETON",
            "GET, /api/openapi.json, SINGLETON",
            "GET, /swagger.json, SINGLETON",
            "POST, /oauth2/device_authorization, SINGLETON",
            "POST, /oauth2/par, SINGLETON",
            "POST, /oauth2/revoke, OPERATION",
            "POST, /oauth2/register, OPERATION",
            "POST, /reports/{id}/terminate, OPERATION",
            "POST, /reports/{id}/duplicate, OPERATION",
            "POST, /reports/{id}/rename, OPERATION",
            "POST, /reports/{id}/move, OPERATION",
            "POST, /reports/{id}/share, OPERATION",
            "POST, /reports/{id}/invite, OPERATION",
            "GET, /rest/api/2/issues, COLLECTION",
            "GET, /api/2.0/clusters, COLLECTION",
            "GET, /2023-10-01/orders, COLLECTION",
            "GET, /v1beta/models, COLLECTION",
            "GET, /v1alpha1/models, COLLECTION",
            "POST, /reports/{id}:cancel, OPERATION",
            "GET, /oauth2/authorize, OPERATION",
            "GET, /oauth/authorize, OPERATION",
            "POST, /oauth/token, SINGLETON",
            "POST, /oauth2/token, SINGLETON",
            "GET, /oauth2/userinfo, SINGLETON",
            "GET, /oauth2/callback, SINGLETON",
            "GET, /.well-known/openid-configuration, SINGLETON",
            "GET, /.well-known/jwks.json, SINGLETON",
            "GET, /me, SINGLETON",
            "GET, /me/orders, COLLECTION",
            "GET, /self, SINGLETON",
            "GET, /reports/{id}/uploadToken, SINGLETON",
            "GET, /reports/{id}/uploadUrl, SINGLETON",
            "GET, /purchaseOrders, COLLECTION",
            "GET, /searchResults, COLLECTION",
            "GET, /exportJobs/{id}, IDENTIFIER",
            "GET, /uploadSessions/{id}, IDENTIFIER",
            "GET, /enterpriseCustomers, COLLECTION"
    })
    void shouldClassifyValidPaths(HttpMethod method, String path, PathNamingRules.SegmentRole lastRole) {
        PathNamingRules.Analysis result = PathNamingRules.analyze(path, method);
        assertThat(result.nounErrors()).isEmpty();
        assertThat(result.pluralErrors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.segments().getLast().role()).isEqualTo(lastRole);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /organizations/resolve/{externalOrgId}, resolve",
            "POST, /reports/schedule/{scheduleId}, schedule",
            "POST, /reports/batch/items, batch",
            "GET, /branding/resolved, resolved",
            "GET, /branding/configuration-resolved, configuration-resolved",
            "GET, /branding/latest, latest",
            "POST, /reports/create-token, create-token",
            "GET, /reports/getReports, getReports",
            "GET, /reports/{id}/cancel, cancel",
            "HEAD, /reports/{id}/cancel, cancel",
            "DELETE, /subscriptions/validate, validate",
            "GET, /searchUsers, searchUsers",
            "POST, /uploadFiles, uploadFiles",
            "GET, /downloadFiles, downloadFiles",
            "POST, /processPayments, processPayments",
            "POST, /registerUsers, registerUsers",
            "POST, /purchaseItems, purchaseItems",
            "GET, /fetchUsers, fetchUsers",
            "GET, /readUsers, readUsers",
            "POST, /saveUsers, saveUsers",
            "POST, /users/{id}/uploadFiles, uploadFiles",
            "GET, /search-users, search-users",
            "GET, /search-products, search-products",
            "GET, /search-orders, search-orders",
            "GET, /search-items, search-items",
            "GET, /searchCustomers, searchCustomers",
            "GET, /stores/{id}/search-products, search-products",
            "GET, /export-reports, export-reports",
            "GET, /export-users, export-users",
            "GET, /download-reports, download-reports",
            "GET, /download-documents, download-documents",
            "GET, /import-users, import-users",
            "GET, /sync-users, sync-users",
            "GET, /start-sessions, start-sessions",
            "GET, /check-tokens, check-tokens",
            "HEAD, /check-users, check-users",
            "POST, /post-users, post-users",
            "POST, /patch-users, patch-users",
            "POST, /reports/{id}/export-data, export-data",
            "POST, /upload-files, upload-files",
            "POST, /process-payments, process-payments",
            "POST, /register-users, register-users",
            "POST, /send-emails, send-emails",
            "POST, /import-users, import-users",
            "POST, /export-reports, export-reports",
            "POST, /users/{id}/send-emails, send-emails",
            "POST, /users/{id}/send-email, send-email",
            "POST, /tickets/{id}/submit-feedback, submit-feedback",
            "POST, /api/searchUsers, searchUsers",
            "POST, /api/v1/send-emails, send-emails",
            "POST, /internal/generate-tokens, generate-tokens",
            "POST, /admin/send-emails, send-emails",
            "POST, /admin/rebuild-indexes, rebuild-indexes",
            "POST, /webhook/publish-events, publish-events",
            "POST, /api/cancel, cancel",
            "POST, /add-users, add-users",
            "POST, /update-users, update-users",
            "DELETE, /delete-users/{id}, delete-users",
            "POST, /delete-records, delete-records",
            "DELETE, /delete-records/{id}, delete-records",
            "POST, /update-records, update-records",
            "POST, /add-events, add-events",
            "POST, /update-rules, update-rules",
            "POST, /delete-logs, delete-logs",
            "POST, /add-notes, add-notes",
            "PUT, /update-status, update-status",
            "DELETE, /users/{id}/delete-history, delete-history",
            "POST, /users/{id}/update-status, update-status",
            "POST, /publish-events, publish-events",
            "POST, /generate-tokens, generate-tokens",
            "POST, /start-sessions, start-sessions",
            "POST, /register-devices, register-devices",
            "POST, /start-jobs, start-jobs",
            "POST, /stop-jobs, stop-jobs",
            "POST, /copy-templates, copy-templates",
            "POST, /lock-devices, lock-devices",
            "POST, /sync-devices, sync-devices",
            "POST, /transfer-tokens, transfer-tokens",
            "POST, /claim-codes, claim-codes",
            "POST, /check-tokens, check-tokens",
            "POST, /import-events, import-events",
            "POST, /export-events, export-events",
            "POST, /capture-events, capture-events",
            "POST, /stream-events, stream-events",
            "POST, /users/{id}/start-sessions, start-sessions",
            "POST, /api/start-jobs, start-jobs",
            "POST, /sync-records, sync-records",
            "POST, /export-logs, export-logs",
            "POST, /addEvents, addEvents",
            "POST, /updateRecords, updateRecords",
            "POST, /deleteLogs, deleteLogs",
            "POST, /sendEvents, sendEvents",
            "POST, /scheduleJobs, scheduleJobs",
            "GET, /cancel-reasons, cancel-reasons",
            "GET, /rotate-policies, rotate-policies",
            "GET, /verify-tokens, verify-tokens",
            "GET, /payments/{id}/authorize, authorize",
            "GET, /orders/{id}/authorize, authorize",
            "GET, /reports/{id}.json, {id}.json",
            "GET, /reports.json, reports.json",
            "GET, /reports/summary.pdf, summary.pdf",
            "GET, /reports/{id}/report.pdf, report.pdf",
            "GET, /files/{id}/document.docx, document.docx",
            "GET, /reports/{id}/suspend, suspend",
            "GET, /reports/{id}/void, void",
            "GET, /reports/{id}/merge, merge",
            "GET, /reports/{id}/transfer, transfer",
            "GET, /reports/{id}/sign, sign",
            "GET, /reports/{id}/resend, resend",
            "DELETE, /carts/{id}/clear, clear",
            "POST, /reports/remove-item, remove-item",
            "PUT, /reports/{id}/set-owner, set-owner",
            "POST, /users/{id}/fetch, fetch",
            "GET, /reports/{id}:cancel, cancel",
            "DELETE, /reports/{id}:approve, approve",
            "POST, /reports:batchGet, batchGet"
    })
    void shouldReportNounAndOperationViolationsOnce(HttpMethod method, String path, String invalid) {
        PathNamingRules.Analysis result = PathNamingRules.analyze(path, method);
        assertThat(result.nounErrors()).singleElement().asString().contains(invalid);
        assertThat(result.pluralErrors()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "DELETE, /carts/{cartId}/token/{token}, token",
            "POST, /carts/{cartId}/token/{token}, token",
            "GET, /tickets/{ticketId}/evidence-item/{evidenceId}, evidence-item",
            "GET, /report/{reportId}/executions, report",
            "GET, /configuration/{configurationId}, configuration",
            "GET, /admin/{adminId}, admin",
            "GET, /api/{apiId}, api",
            "PATCH, /tickets/{ticketId}/reference-link/{linkId}, reference-link",
            "GET, /customers/{id}/shipping-address/{addressId}, shipping-address",
            "POST, /orders/{id}/delivery-attempt-result/{resultId}, delivery-attempt-result",
            "GET, /reports/{id}/status/{statusId}, status",
            "GET, /audit/{auditId}, audit",
            "GET, /analysis/{analysisId}, analysis",
            "GET, /alias/{aliasId}, alias",
            "GET, /evidence/{evidenceId}, evidence",
            "GET, /bus/{id}, bus",
            "GET, /virus/{id}, virus",
            "GET, /bonus/{id}, bonus",
            "GET, /census/{id}, census",
            "GET, /axis/{id}, axis",
            "GET, /atlas/{id}, atlas",
            "GET, /lens/{id}, lens",
            "GET, /gas/{id}, gas",
            "GET, /bias/{id}, bias",
            "GET, /hypothesis/{id}, hypothesis",
            "GET, /genus/{id}, genus",
            "GET, /iris/{id}, iris",
            "GET, /csv, csv",
            "GET, /purchase-order/{id}, purchase-order"
    })
    void shouldRequirePluralCountableCollectionsBeforeIdentifiers(HttpMethod method, String path, String invalid) {
        PathNamingRules.Analysis result = PathNamingRules.analyze(path, method);
        assertThat(result.nounErrors()).isEmpty();
        assertThat(result.pluralErrors()).singleElement().asString().contains(invalid);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /accounts/{id}/transaction, transaction",
            "GET, /users/{id}/session, session",
            "GET, /accounts/{id}/enterprise, enterprise",
            "GET, /users/{id}/expertise, expertise",
            "GET, /stores/{id}/merchandise, merchandise",
            "GET, /users/{id}/noise-level, noise-level"
    })
    void shouldWarnOnCountableSingularNounsUnderInstance(HttpMethod method, String path, String uncertain) {
        PathNamingRules.Analysis result = PathNamingRules.analyze(path, method);
        assertThat(result.nounErrors()).isEmpty();
        assertThat(result.pluralErrors()).isEmpty();
        assertThat(result.warnings()).singleElement().asString().contains(uncertain);
    }

    @Test
    void shouldClassifyOperationOrSingletonSegmentsForMethodConsistency() {
        assertThat(PathNamingRules.isOperationOrSingletonSegment("/reports/{id}/cancel")).isTrue();
        assertThat(PathNamingRules.isOperationOrSingletonSegment("/branding/resolved-configuration")).isTrue();
        assertThat(PathNamingRules.isOperationOrSingletonSegment("/users/{id}/me")).isTrue();
        assertThat(PathNamingRules.isOperationOrSingletonSegment("/refund-requests")).isFalse();
        assertThat(PathNamingRules.isOperationOrSingletonSegment("/restore-points")).isFalse();
        assertThat(PathNamingRules.isOperationOrSingletonSegment("/evidence")).isFalse();
        assertThat(PathNamingRules.isOperationOrSingletonSegment("/reports/{id}")).isFalse();
    }

    @Test
    void shouldTreatPostToHomographCollectionAsValidWhenItemPathExistsInSpec() {
        PathNamingRules.Analysis withItemPath = PathNamingRules.analyze(
                "/sync-jobs", HttpMethod.POST, Set.of("/sync-jobs", "/sync-jobs/{id}"));
        assertThat(withItemPath.nounErrors()).isEmpty();
        assertThat(withItemPath.pluralErrors()).isEmpty();
        assertThat(withItemPath.segments().getLast().role()).isEqualTo(PathNamingRules.SegmentRole.COLLECTION);

        PathNamingRules.Analysis withoutItemPath = PathNamingRules.analyze(
                "/sync-jobs", HttpMethod.POST, Set.of("/sync-jobs"));
        assertThat(withoutItemPath.nounErrors()).isEmpty();
        assertThat(withoutItemPath.pluralErrors()).isEmpty();
        assertThat(withoutItemPath.warnings()).singleElement().asString()
                .contains("sync-jobs")
                .contains("verb-prefixed collection without item path or list GET; confirm the name");
    }

    @Test
    void shouldClassifyUnlistedHomographCompoundWithItemPathAsCollectionWithWarning() {
        Set<String> specPaths = Set.of(
                "/repos/{owner}/{repo}/check-runs",
                "/repos/{owner}/{repo}/check-runs/{check_run_id}",
                "/repos/{owner}/{repo}/commits/{ref}/check-runs",
                "/stop-areas/{id}");

        PathNamingRules.Analysis collectionPost = PathNamingRules.analyze(
                "/repos/{owner}/{repo}/check-runs", HttpMethod.POST, specPaths);
        assertThat(collectionPost.nounErrors()).isEmpty();
        assertThat(collectionPost.pluralErrors()).isEmpty();
        assertThat(collectionPost.warnings()).singleElement().asString()
                .contains("check-runs: verb-prefixed collection name; confirm or rename");
        assertThat(collectionPost.segments().getLast().role()).isEqualTo(PathNamingRules.SegmentRole.COLLECTION);

        PathNamingRules.Analysis secondaryCollectionGet = PathNamingRules.analyze(
                "/repos/{owner}/{repo}/commits/{ref}/check-runs", HttpMethod.GET, specPaths);
        assertThat(secondaryCollectionGet.nounErrors()).isEmpty();
        assertThat(secondaryCollectionGet.pluralErrors()).isEmpty();
        assertThat(secondaryCollectionGet.warnings()).singleElement().asString()
                .contains("check-runs: verb-prefixed collection name; confirm or rename");
        assertThat(secondaryCollectionGet.segments().getLast().role()).isEqualTo(PathNamingRules.SegmentRole.COLLECTION);

        PathNamingRules.Analysis itemGet = PathNamingRules.analyze(
                "/repos/{owner}/{repo}/check-runs/{check_run_id}", HttpMethod.GET, specPaths);
        assertThat(itemGet.nounErrors()).isEmpty();
        assertThat(itemGet.pluralErrors()).isEmpty();
        assertThat(itemGet.warnings()).isEmpty();
        assertThat(itemGet.segments().getLast().role()).isEqualTo(PathNamingRules.SegmentRole.IDENTIFIER);

        PathNamingRules.Analysis itemOnlyGet = PathNamingRules.analyze(
                "/stop-areas/{id}", HttpMethod.GET, specPaths);
        assertThat(itemOnlyGet.nounErrors()).isEmpty();
        assertThat(itemOnlyGet.pluralErrors()).isEmpty();
        assertThat(itemOnlyGet.warnings()).singleElement().asString()
                .contains("stop-areas: verb-prefixed collection name; confirm or rename");

        Set<String> nestedOnlySpec = Set.of(
                "/start-jobs/{id}/logs",
                "/start-jobs/{id}/logs/{logId}");
        PathNamingRules.Analysis nestedFirst = PathNamingRules.analyze(
                "/start-jobs/{id}/logs", HttpMethod.GET, nestedOnlySpec);
        assertThat(nestedFirst.warnings()).singleElement().asString()
                .contains("start-jobs: verb-prefixed collection name; confirm or rename");
        PathNamingRules.Analysis nestedDeeper = PathNamingRules.analyze(
                "/start-jobs/{id}/logs/{logId}", HttpMethod.GET, nestedOnlySpec);
        assertThat(nestedDeeper.warnings()).isEmpty();

        Set<String> versionedSpec = Set.of(
                "/api/v1/stop-areas",
                "/api/v1/stop-areas/{id}");
        assertThat(PathNamingRules.analyze("/api/v1/stop-areas", HttpMethod.GET, versionedSpec).warnings())
                .singleElement().asString().contains("stop-areas");
        assertThat(PathNamingRules.analyze("/api/v1/stop-areas/{id}", HttpMethod.GET, versionedSpec).warnings())
                .isEmpty();
    }
}
