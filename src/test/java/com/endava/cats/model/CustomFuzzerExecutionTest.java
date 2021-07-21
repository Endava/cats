package com.endava.cats.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CustomFuzzerExecutionTest {

    @Test
    void shouldCompareOnPathThenTestId() {
        CustomFuzzerExecution cfe1 = buildCfe("path1", "test1");
        CustomFuzzerExecution cfe2 = buildCfe("path2", "test2");

        Assertions.assertThat(cfe1).isLessThan(cfe2);
    }

    @Test
    void shouldBeEqual() {
        CustomFuzzerExecution cfe1 = buildCfe("path1", "test1");
        CustomFuzzerExecution cfe2 = buildCfe("path1", "test1");

        Assertions.assertThat(cfe1).isEqualTo(cfe2);
    }

    @Test
    void shouldHaveSameHash() {
        CustomFuzzerExecution cfe1 = buildCfe("path1", "test1");
        CustomFuzzerExecution cfe2 = buildCfe("path1", "test1");

        Assertions.assertThat(cfe1).hasSameHashCodeAs(cfe2);
    }

    private CustomFuzzerExecution buildCfe(String path, String testId) {
        FuzzingData data1 = FuzzingData.builder().path(path).build();
        return CustomFuzzerExecution.builder()
                .fuzzingData(data1).testId(testId).build();
    }
}
