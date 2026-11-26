package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class AwsArnGeneratorTest {
    private AwsArnGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new AwsArnGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "arn,,true", "aws-arn,,true", "awsArn,,true", "aws_arn,,true",
            ",arn,true", ",roleArn,true", ",resource#topicArn,true", ",queue_arn,true", ",bucketArn,true",
            "other,other,false", ",mrn,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidAwsArn() {
        String arn = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(arn).matches("^arn:aws:[a-z0-9]+:[a-z]{2}-[a-z]+-\\d:\\d{12}:resource/[a-z0-9]{8}$");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("arn:aws:iam:us-east-1:12345:role/test");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("not:an:arn");
        Assertions.assertThat(generator.matchingFormats()).contains("arn", "aws-arn", "awsArn", "aws_arn");
    }
}
