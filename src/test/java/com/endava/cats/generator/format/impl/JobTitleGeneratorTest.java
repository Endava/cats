package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class JobTitleGeneratorTest {

    @Test
    void shouldGenerate() {
        JobTitleGenerator jobTitleGenerator = new JobTitleGenerator();
        Assertions.assertThat(jobTitleGenerator.generate(new Schema<>()).toString()).containsAnyOf(
                JobTitleGenerator.JOB_TITLES.toArray(new String[0]));
    }

    @ParameterizedTest
    @CsvSource({"jobTitle,true", "other,false", "job-title,true"})
    void shouldApplyToFormat(String format, boolean expected) {
        JobTitleGenerator jobTitleGenerator = new JobTitleGenerator();
        Assertions.assertThat(jobTitleGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"jobTitle,true", "JobTitle,true", "other,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        JobTitleGenerator jobTitleGenerator = new JobTitleGenerator();
        Assertions.assertThat(jobTitleGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}