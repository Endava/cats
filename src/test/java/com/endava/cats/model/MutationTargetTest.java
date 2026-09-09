package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class MutationTargetTest {

    @Test
    void shouldClassifyFieldsUsingRequestMetadata() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPath()).thenReturn("https://localhost/orders/order-1?state=ACTIVE&name=other");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"name\":\"sample\"}");
        Mockito.when(data.getQueryParams()).thenReturn(Set.of("filter"));
        Mockito.when(data.getHeaders()).thenReturn(Set.of(CatsHeader.builder().name("X-Test").build()));

        Assertions.assertThat(MutationTarget.requestField(data, "state"))
                .isEqualTo(MutationTarget.query("state"));
        Assertions.assertThat(MutationTarget.requestField(data, "filter"))
                .isEqualTo(MutationTarget.query("filter"));
        Assertions.assertThat(MutationTarget.requestField(data, "order-1"))
                .isEqualTo(MutationTarget.body("order-1"));
        Assertions.assertThat(MutationTarget.requestField(data, "x-test"))
                .isEqualTo(MutationTarget.header("x-test"));
        Assertions.assertThat(MutationTarget.requestField(data, "name"))
                .isEqualTo(MutationTarget.query("name"));
        Assertions.assertThat(MutationTarget.requestFields(data, "name"))
                .containsExactly(MutationTarget.query("name"), MutationTarget.body("name"));
    }

    @Test
    void shouldRecognizeNamedPathParameters() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPath()).thenReturn("/orders/{orderId}");
        Mockito.when(data.getQueryParams()).thenReturn(Set.of());
        Mockito.when(data.getHeaders()).thenReturn(Set.of());

        Assertions.assertThat(MutationTarget.requestField(data, "orderId"))
                .isEqualTo(MutationTarget.path("orderId"));
    }

    @Test
    void shouldNotTreatLiteralPathSegmentsAsPathParameters() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPath()).thenReturn("/orders/status");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"status\":\"ACTIVE\"}");
        Mockito.when(data.getQueryParams()).thenReturn(Set.of());
        Mockito.when(data.getHeaders()).thenReturn(Set.of());

        Assertions.assertThat(MutationTarget.requestField(data, "status"))
                .isEqualTo(MutationTarget.body("status"));
    }

    @Test
    void shouldNotReportInternalGetPayloadFieldsAsBodyMutations() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(data.getPath()).thenReturn("/orders");
        Mockito.when(data.getPayload()).thenReturn("{\"filter\":{\"state\":\"ACTIVE\"}}");
        Mockito.when(data.getQueryParams()).thenReturn(Set.of("filter"));
        Mockito.when(data.getHeaders()).thenReturn(Set.of());

        Assertions.assertThat(MutationTarget.requestFields(data, "filter#state"))
                .containsExactly(MutationTarget.query("filter#state"));
    }
}
