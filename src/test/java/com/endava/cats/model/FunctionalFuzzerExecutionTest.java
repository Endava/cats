package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


@QuarkusTest
class FunctionalFuzzerExecutionTest {

    @Test
    void shouldNotEqualWhenDiffObject() {
        CustomFuzzerExecution cfe1 = buildCfe("path1", "test1");
        Assertions.assertThat(cfe1).isNotEqualTo("");
    }

    @Test
    void shouldNotEqualWhenDiffObjectNull() {
        CustomFuzzerExecution cfe1 = buildCfe("path1", "test1");
        Assertions.assertThat(cfe1).isNotEqualTo(null);
    }

    @ParameterizedTest
    @CsvSource({"test1,test2,path1,path1", "test1,test1,path1,path2", "test1,test2,path1,path2"})
    void shouldCompareOnPathThenTestId(String test1, String test2, String path1, String path2) {
        CustomFuzzerExecution cfe1 = buildCfe(path1, test1);
        CustomFuzzerExecution cfe2 = buildCfe(path2, test2);

        Assertions.assertThat(cfe1).isNotEqualTo(cfe2);
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
