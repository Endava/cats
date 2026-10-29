package com.endava.cats.fuzzer.contract.util;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HttpMethodConsistencyAnalyzerTest {
    private final HttpMethodConsistencyAnalyzer analyzer = new HttpMethodConsistencyAnalyzer();

    @Test
    void shouldHandleNullOpenApi() {
        HttpMethodConsistencyAnalyzer.ResourcePathData resourceData = analyzer.collectResourceData(null);

        Assertions.assertThat(resourceData.getGroups()).isEmpty();
    }

    @Test
    void shouldHandleOpenApiWithoutPaths() {
        HttpMethodConsistencyAnalyzer.ResourcePathData resourceData = analyzer.collectResourceData(new OpenAPI());

        Assertions.assertThat(resourceData.getGroups()).isEmpty();
    }

    @Test
    void shouldPreserveDeepNestedResourceGroupsWithoutTruncation() {
        Assertions.assertThat(analyzer.extractResourceGroupKey("/api/v1/users/{userId}/accounts/{accountId}"))
                .isEqualTo("/api/v1/users/{}/accounts");
        Assertions.assertThat(analyzer.isActionPath("/users/{userId}/documents/validate")).isTrue();
        Assertions.assertThat(analyzer.isActionPath("/settings/resolved")).isTrue();
    }
}
