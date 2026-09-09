package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

@QuarkusTest
class RequestTargetResolverTest {

    @Test
    void shouldTreatBodyPayloadMutationAsBodyEvenWhenAQueryParameterHasTheSameName() {
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/orders?name=query-value")
                .processedPayload("{\"name\":\"body-value\"}")
                .queryParams(Set.of("name"))
                .headers(Set.of())
                .build();

        Assertions.assertThat(RequestTargetResolver.resolvePayloadField(data, "name"))
                .containsExactly(RequestTarget.body("name"));
    }

    @Test
    void shouldResolveEveryParameterLocationChangedByANonBodyPayload() {
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.GET)
                .path("/orders/{id}?id=original")
                .processedPayload("{\"id\":\"original\"}")
                .queryParams(Set.of("id"))
                .headers(Set.of())
                .build();

        Assertions.assertThat(RequestTargetResolver.resolvePayloadField(data, "id"))
                .containsExactly(RequestTarget.path("id"), RequestTarget.query("id"));
    }

    @Test
    void shouldResolveMultipleBodyFieldsInDeterministicOrder() {
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST)
                .processedPayload("{\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-02\"}")
                .headers(Set.of())
                .build();

        Assertions.assertThat(RequestTargetResolver.resolvePayloadFields(data, Set.of("startDate", "endDate")))
                .containsExactly(RequestTarget.body("endDate"), RequestTarget.body("startDate"));
    }

    @Test
    void shouldResolveLiteralTemplatePathFromTheActualRequestChange() {
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.GET)
                .path("https://localhost/users/FUZZ?state=ACTIVE")
                .processedPayload("{}")
                .queryParams(Set.of("state"))
                .headers(Set.of())
                .build();
        CatsRequest mutated = CatsRequest.builder()
                .httpMethod("GET")
                .url("https://localhost/users/user-42?state=ACTIVE")
                .payload("{}")
                .headers(List.of())
                .build();

        Assertions.assertThat(RequestTargetResolver.resolveTemplateMutation(data, mutated, "FUZZ"))
                .containsExactly(RequestTarget.path("FUZZ"));
    }

    @Test
    void shouldResolveAllTemplateLocationsThatActuallyChanged() {
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("https://localhost/orders?name=old")
                .processedPayload("{\"name\":\"old\"}")
                .queryParams(Set.of("name"))
                .headers(Set.of(CatsHeader.builder().name("name").value("old").build()))
                .build();
        CatsRequest mutated = CatsRequest.builder()
                .httpMethod("POST")
                .url("https://localhost/orders?name=new")
                .payload("{\"name\":\"new\"}")
                .headers(List.of(new KeyValuePair<>("name", "new")))
                .build();

        Assertions.assertThat(RequestTargetResolver.resolveTemplateMutation(data, mutated, "name"))
                .containsExactly(RequestTarget.body("name"), RequestTarget.header("name"),
                        RequestTarget.query("name"));
    }

    @Test
    void shouldOnlyReportCustomRequestPartsThatChanged() {
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/orders/{orderId}")
                .processedPayload("{\"name\":\"old\"}")
                .pathParamsPayload("{\"orderId\":\"old\"}")
                .queryParams(Set.of())
                .headers(Set.of(CatsHeader.builder().name("X-Test").value("old").build()))
                .build();

        Assertions.assertThat(RequestTargetResolver.resolveCustomMutations(data,
                        "{\"name\":\"new\"}", "{\"orderId\":\"new\"}",
                        Set.of(CatsHeader.builder().name("X-Test").value("old").build()),
                        Set.of("name", "orderId", "X-Test")))
                .containsExactly(RequestTarget.body("name"), RequestTarget.path("orderId"));
    }
}
