package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generates valid and invalid Amazon Resource Names (ARN).
 */
@Singleton
public class AwsArnGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> SERVICES = List.of("s3", "iam", "sns", "sqs", "lambda", "dynamodb", "kms", "ec2");
    private static final List<String> REGIONS = List.of("us-east-1", "us-west-2", "eu-west-1", "eu-central-1", "ap-southeast-1");
    private static final Set<String> FORMATS = Set.of("arn", "awsarn");
    private static final List<String> PROPERTY_SUFFIXES = List.of("arn", "awsarn", "rolearn", "resourcearn", "topicarn", "queuearn", "bucketarn");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return FORMATS.contains(sanitizedFormat)
                || PROPERTY_SUFFIXES.stream().anyMatch(sanitizedProperty::endsWith);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("arn", "aws-arn", "awsArn", "aws_arn");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String service = CatsUtil.selectRandom(SERVICES);
        String region = CatsUtil.selectRandom(REGIONS);
        String accountId = FormatGeneratorUtil.randomDigits(12);
        String resourceId = CatsRandom.alphanumeric(8).toLowerCase(Locale.ROOT);
        String arn = "arn:aws:%s:%s:%s:resource/%s".formatted(service, region, accountId, resourceId);
        return DataFormat.matchesPatternOrNull(schema, arn);
    }

    @Override
    public String getAlmostValidValue() {
        return "arn:aws:iam:us-east-1:12345:role/test";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not:an:arn";
    }
}
