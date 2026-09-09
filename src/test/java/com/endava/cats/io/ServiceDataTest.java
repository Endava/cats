package com.endava.cats.io;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.QueryParameterSerialization;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class ServiceDataTest {

    @Test
    void shouldKeepMutationAndResponseValidationMetadataSeparate() {
        ServiceData data = ServiceData.builder()
                .relativePath("/orders/{orderId}")
                .queryParams(Set.of("filter"))
                .responseValidationField("customer#name")
                .mutationTarget(RequestTarget.body("customer#name"))
                .mutationTarget(RequestTarget.path("orderId"))
                .mutationTarget(RequestTarget.query("filter"))
                .mutationTarget(RequestTarget.header("X-Test"))
                .mutationTarget(RequestTarget.requestBody())
                .build();

        Assertions.assertThat(data.getAllMutationTargets()).containsExactly(
                RequestTarget.body("customer#name"),
                RequestTarget.header("X-Test"),
                RequestTarget.path("orderId"),
                RequestTarget.query("filter"),
                RequestTarget.requestBody());
        Assertions.assertThat(data.getResponseValidationFields()).containsExactly("customer#name");
    }

    @Test
    void shouldDeduplicateExplicitTargets() {
        ServiceData data = ServiceData.builder()
                .relativePath("/orders")
                .mutationTarget(RequestTarget.body("name"))
                .mutationTarget(RequestTarget.body("name"))
                .build();

        Assertions.assertThat(data.getAllMutationTargets()).containsExactly(RequestTarget.body("name"));
    }

    @Test
    void shouldReportWhetherRequestHasDeclaredMutations() {
        Assertions.assertThat(ServiceData.builder().build().hasDeclaredMutation()).isFalse();
        Assertions.assertThat(ServiceData.builder().mutationTarget(RequestTarget.requestBody()).build()
                .hasDeclaredMutation()).isTrue();
    }

    @Test
    void shouldCreateServiceDataFromCommonFuzzingDataMetadata() {
        CatsHeader header = CatsHeader.builder().name("X-Test").value("value").build();
        QueryParameterSerialization serialization = new QueryParameterSerialization("form", false);
        FuzzingData fuzzingData = FuzzingData.builder()
                .path("/orders/{orderId}")
                .contractPath("/orders/{orderId}")
                .headers(Set.of(header))
                .payload("{\"orderId\":\"42\"}")
                .processedPayload("{\"orderId\":\"42\"}")
                .queryParams(Set.of("state"))
                .queryParameterSerializations(Map.of("state", serialization))
                .method(HttpMethod.POST)
                .requestContentTypes(List.of("application/json"))
                .pathParamsPayload("{\"orderId\":\"42\"}")
                .build();

        ServiceData data = ServiceData.from(fuzzingData).build();

        Assertions.assertThat(data.getRelativePath()).isEqualTo(fuzzingData.getPath());
        Assertions.assertThat(data.getContractPath()).isEqualTo(fuzzingData.getContractPath());
        Assertions.assertThat(data.getHeaders()).containsExactly(header);
        Assertions.assertThat(data.getPayload()).isEqualTo(fuzzingData.getPayload());
        Assertions.assertThat(data.getOriginalPayload()).isEqualTo(fuzzingData.getPayload());
        Assertions.assertThat(data.getQueryParams()).containsExactly("state");
        Assertions.assertThat(data.getQueryParameterSerializations()).containsEntry("state", serialization);
        Assertions.assertThat(data.getHttpMethod()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(data.getContentType()).isEqualTo("application/json");
        Assertions.assertThat(data.getPathParamsPayload()).isEqualTo("{\"orderId\":\"42\"}");
    }
}
