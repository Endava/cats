package com.endava.cats.generator.simple;

import com.github.curiousoddman.rgxgen.RgxGen;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

public class StringGenerator {
    public static final String FUZZ = "fuzz";
    public static final int DEFAULT_MAX_LENGTH = 2000;
    public static final String ALPHANUMERIC = "[a-zA-Z0-9]";

    private StringGenerator() {
        //ntd
    }

    public static String generateRandomString() {
        return FUZZ;
    }

    public static String generateLargeString(int times) {
        return StringUtils.repeat(FUZZ, times);
    }

    public static String generate(String pattern, int min, int max) {
        String completePattern = sanitize(pattern);
        if (!completePattern.endsWith("}")) {
            completePattern = completePattern + "{" + min + "," + max + "}";
        }

        return new RgxGen(completePattern).generate();
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

    public static String sanitize(String pattern) {
        if (pattern.equalsIgnoreCase("^(?!\\s*$).+")) {
            pattern = ALPHANUMERIC;
        }
        if (pattern.startsWith("^")) {
            pattern = pattern.substring(1);
        }
        if (pattern.endsWith("$") || pattern.endsWith("+") || pattern.endsWith("*")) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        return pattern;
    }
}