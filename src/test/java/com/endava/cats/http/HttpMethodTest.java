// src/test/java/com/endava/cats/http/HttpMethodTest.java
package com.endava.cats.http;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class HttpMethodTest {

    @Test
    void shouldReturnRestMethods() {
        List<HttpMethod> rest = HttpMethod.restMethods();
        assertThat(rest).containsExactly(
                HttpMethod.POST, HttpMethod.PUT, HttpMethod.GET,
                HttpMethod.TRACE, HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD);
    }

    @Test
    void shouldReturnNonRestMethods() {
        List<HttpMethod> nonRest = HttpMethod.nonRestMethods();
        assertThat(nonRest).contains(
                HttpMethod.CONNECT, HttpMethod.COPY, HttpMethod.MOVE,
                HttpMethod.PROPPATCH, HttpMethod.PROPFIND, HttpMethod.MKCOL,
                HttpMethod.LOCK, HttpMethod.UNLOCK, HttpMethod.SEARCH,
                HttpMethod.BIND, HttpMethod.UNBIND, HttpMethod.REBIND,
                HttpMethod.MKREDIRECTREF, HttpMethod.UPDATEREDIRECTREF,
                HttpMethod.ORDERPATCH, HttpMethod.ACL, HttpMethod.REPORT
        );
    }

    @Test
    void shouldReturnHypotheticalMethods() {
        List<HttpMethod> hypo = HttpMethod.hypotheticalMethods();
        assertThat(hypo).contains(
                HttpMethod.DIFF, HttpMethod.VERIFY, HttpMethod.PUBLISH, HttpMethod.UNPUBLISH,
                HttpMethod.BATCH, HttpMethod.VIEW, HttpMethod.PURGE, HttpMethod.DEBUG,
                HttpMethod.SUBSCRIBE, HttpMethod.UNSUBSCRIBE, HttpMethod.MERGE, HttpMethod.INDEX
        );
    }

    @Test
    void listsShouldCoverAllValuesWithoutOverlapIssues() {
        Set<HttpMethod> union = EnumSet.noneOf(HttpMethod.class);
        union.addAll(HttpMethod.restMethods());
        union.addAll(HttpMethod.nonRestMethods());
        union.addAll(HttpMethod.hypotheticalMethods());
        // Some enum constants (e.g. PROPPATCH, REPORT) appear in non-rest list and also in requiresBody logic
        assertThat(union).contains(HttpMethod.values());
    }

    @Test
    void requiresBodyEnum() {
        assertThat(HttpMethod.requiresBody(HttpMethod.POST)).isTrue();
        assertThat(HttpMethod.requiresBody(HttpMethod.PUT)).isTrue();
        assertThat(HttpMethod.requiresBody(HttpMethod.PATCH)).isTrue();
        assertThat(HttpMethod.requiresBody(HttpMethod.PROPPATCH)).isTrue();
        assertThat(HttpMethod.requiresBody(HttpMethod.REPORT)).isTrue();

        assertThat(HttpMethod.requiresBody(HttpMethod.GET)).isFalse();
        assertThat(HttpMethod.requiresBody(HttpMethod.DELETE)).isFalse();
        assertThat(HttpMethod.requiresBody(HttpMethod.HEAD)).isFalse();
    }

    @Test
    void requiresBodyString() {
        assertThat(HttpMethod.requiresBody("POST")).isTrue();
        assertThat(HttpMethod.requiresBody("PUT")).isTrue();
        assertThatThrownBy(() -> HttpMethod.requiresBody("post"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpMethod.requiresBody("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getOperationSupported() {
        PathItem pathItem = new PathItem();
        Operation op = new Operation();
        pathItem.setGet(op);

        Operation returned = HttpMethod.getOperation(HttpMethod.GET, pathItem);
        assertThat(returned).isSameAs(op);
    }

    @Test
    void getOperationSupportedButNotDefinedReturnsNull() {
        PathItem pathItem = new PathItem(); // no GET set
        assertThat(HttpMethod.getOperation(HttpMethod.GET, pathItem)).isNull();
    }

    @Test
    void getOperationUnsupportedThrows() {
        PathItem pathItem = new PathItem();
        assertThatThrownBy(() -> HttpMethod.getOperation(HttpMethod.CONNECT, pathItem))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported HTTP method: CONNECT");
    }

    @Test
    void fromStringCaseInsensitiveAndUnknownAndNull() {
        Optional<HttpMethod> postLower = HttpMethod.fromString("post");
        assertThat(postLower).contains(HttpMethod.POST);

        Optional<HttpMethod> unknown = HttpMethod.fromString("something");
        assertThat(unknown).isEmpty();

        Optional<HttpMethod> nullVal = HttpMethod.fromString(null);
        assertThat(nullVal).isEmpty();
    }

    @Test
    void recommendedCodesPresentForMappedMethods() {
        assertThat(HttpMethod.POST.getRecommendedCodes()).containsExactlyInAnyOrder("400", "500", "200|201|202|204");
        assertThat(HttpMethod.PUT.getRecommendedCodes()).containsExactlyInAnyOrder("400", "404", "500", "200|201|202|204");
        assertThat(HttpMethod.GET.getRecommendedCodes()).containsExactlyInAnyOrder("404", "500", "200|202");
        assertThat(HttpMethod.HEAD.getRecommendedCodes()).containsExactlyInAnyOrder("404", "200|202");
        assertThat(HttpMethod.DELETE.getRecommendedCodes()).containsExactlyInAnyOrder("404", "500", "200|201|202|204");
        assertThat(HttpMethod.PATCH.getRecommendedCodes()).containsExactlyInAnyOrder("400", "404", "500", "200|201|202|204");
        assertThat(HttpMethod.TRACE.getRecommendedCodes()).containsExactlyInAnyOrder("500", "200");
    }

    @Test
    void recommendedCodesNullForUnmapped() {
        assertThat(HttpMethod.CONNECT.getRecommendedCodes()).isNull();
        assertThat(HttpMethod.REPORT.getRecommendedCodes()).isNull();
    }
}
