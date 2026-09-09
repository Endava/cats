package com.endava.cats.io;

import com.endava.cats.model.MutationTarget;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

@QuarkusTest
class ServiceDataTest {

    @Test
    void shouldCombineExplicitTargetsWithExistingExecutorMetadata() {
        ServiceData data = ServiceData.builder()
                .relativePath("/orders/{orderId}")
                .queryParams(Set.of("filter"))
                .fuzzedField("customer#name")
                .fuzzedField("orderId")
                .fuzzedField("filter")
                .fuzzedHeader("X-Test")
                .mutationTarget(MutationTarget.requestBody())
                .build();

        Assertions.assertThat(data.getAllMutationTargets()).containsExactly(
                MutationTarget.body("customer#name"),
                MutationTarget.header("X-Test"),
                MutationTarget.path("orderId"),
                MutationTarget.query("filter"),
                MutationTarget.requestBody());
    }

    @Test
    void shouldDeduplicateExplicitAndDerivedTargets() {
        ServiceData data = ServiceData.builder()
                .relativePath("/orders")
                .fuzzedField("name")
                .mutationTarget(MutationTarget.body("name"))
                .build();

        Assertions.assertThat(data.getAllMutationTargets()).containsExactly(MutationTarget.body("name"));
    }

    @Test
    void shouldClassifyNestedObjectQueryFieldsByTheirRootParameter() {
        ServiceData data = ServiceData.builder()
                .relativePath("/orders")
                .queryParams(Set.of("filter"))
                .fuzzedField("filter#state")
                .build();

        Assertions.assertThat(data.getAllMutationTargets())
                .containsExactly(MutationTarget.query("filter#state"));
    }
}
