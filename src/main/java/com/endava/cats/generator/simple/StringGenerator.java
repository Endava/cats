package com.endava.cats.generator.simple;

import com.github.curiousoddman.rgxgen.RgxGen;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;

public class StringGenerator {
    public static final String FUZZ = "fuzz";
    public static final int DEFAULT_MAX_LENGTH = 10000;
    public static final String ALPHANUMERIC = "[a-zA-Z0-9]";
    private static final SecureRandom RANDOM = new SecureRandom();

    private StringGenerator() {
        //ntd
    }

    public static String generateRandomString() {
        return FUZZ;
    }

    public static String generateLargeString(int times) {
        return StringUtils.repeat(FUZZ, times);
    }

    /**
     * This method generates a random string according to the given input. If the pattern already has length information the min/max will be ignored.
     *
     * @param pattern the regex pattern
     * @param min     min length of the generated string
     * @param max     max length of the generated string
     * @return a random string corresponding to the given pattern and min, max restrictions
     */
    public static String generate(String pattern, int min, int max) {
        String generatedValue = new RgxGen(pattern).generate();
        if (pattern.endsWith("}")) {
            return generatedValue;
        }
        return composeString(generatedValue, min, max);
    }

    public static String composeString(String initial, int min, int max) {
        String trimmed = initial.trim().replaceAll("[\\p{Z}]+", "");
        if (trimmed.length() < min) {
            return composeString(trimmed + trimmed, min, max);
        } else if (trimmed.length() > max) {
            int random = max == min ? 0 : RANDOM.nextInt(max - min);
            return trimmed.substring(0, max - random);
        }

        return trimmed;
    }

    /**
     * Generates a random right boundary String value. If the maxLength of the associated schema is between {@code Integer.MAX_VALUE - 10}
     * and {@code Integer.MAX_VALUE} (including), the generated String length will be {@code Integer.MAX_VALUE - 2}, which is the maximum length
     * allowed for an char array on most JVMs. If the maxLength is less than
     * {@code Integer.MAX_VALUE - 10}, then the generated value will have maxLength + 10 length. If the Schema has no maxLength
     * defined, it will default to {@link DEFAULT_MAX_LENGTH}.
     *
     * @param schema the associated schema of current fuzzed field
     * @return a random String whose length is bigger than maxLength
     */
    public static String generateRightBoundString(Schema<?> schema) {
        long minLength = getRightBoundaryLength(schema);
        return StringUtils.repeat('a', (int) minLength);
    }

    public static long getRightBoundaryLength(Schema<?> schema) {
        long minLength = schema.getMaxLength() != null ? schema.getMaxLength().longValue() + 10 : DEFAULT_MAX_LENGTH;

        if (minLength > Integer.MAX_VALUE) {
            minLength = Integer.MAX_VALUE - 2L;
        }
        return minLength;
    }

    public static String generateLeftBoundString(Schema<?> schema) {
        int minLength = schema.getMinLength() != null ? schema.getMinLength() - 1 : 0;

        if (minLength <= 0) {
            return "";
        }
        String pattern = ALPHANUMERIC + "{" + (minLength - 1) + "," + minLength + "}";

        return new RgxGen(pattern).generate();
    }


}