package com.endava.cats.generator.simple;

import com.github.curiousoddman.rgxgen.RgxGen;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;

public class StringGenerator {
    public static final String FUZZ = "fuzz";
    public static final int DEFAULT_MAX_LENGTH = 2000;
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

    public static String generateRightBoundString(Schema schema) {
        int minLength = schema.getMaxLength() != null ? schema.getMaxLength() + 10 : DEFAULT_MAX_LENGTH;

        String pattern = ALPHANUMERIC + "{" + minLength + ",}";
        return new RgxGen(pattern).generate();
    }

    public static String generateLeftBoundString(Schema schema) {
        int minLength = schema.getMinLength() != null ? schema.getMinLength() - 1 : 0;

        if (minLength <= 0) {
            return "";
        }
        String pattern = ALPHANUMERIC + "{" + (minLength - 1) + "," + minLength + "}";

        return new RgxGen(pattern).generate();
    }


}