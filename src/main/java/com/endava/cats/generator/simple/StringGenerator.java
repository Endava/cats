package com.endava.cats.generator.simple;

import com.mifmif.common.regex.Generex;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

public class StringGenerator {
    public static final String FUZZ = "fuzz";
    public static final int DEFAULT_MAX_LENGTH = 2000;
    private static final String ALPHANUMERIC = "[a-zA-Z0-9]*";

    private StringGenerator() {
        //ntd
    }

    public static String generateRandomString() {
        return FUZZ;
    }

    public static String generateLargeString(int times) {
        return StringUtils.repeat(FUZZ, times);
    }

    public static String generateRightBoundString(Schema schema) {
        Generex generex = new Generex(ALPHANUMERIC);

        int minLength = schema.getMaxLength() != null ? schema.getMaxLength() + 10 : DEFAULT_MAX_LENGTH;

        return generex.random(minLength);
    }

    public static String generateLeftBoundString(Schema schema) {
        int minLength = schema.getMinLength() != null ? schema.getMinLength() - 1 : 0;

        if (minLength <= 0) {
            return "";
        }
        Generex generex = new Generex(ALPHANUMERIC);

        return generex.random(minLength - 1, minLength);
    }

    public static String sanitize(String pattern) {
        if (pattern.equalsIgnoreCase("^(?!\\s*$).+")) {
            pattern = ALPHANUMERIC;
        }
        if (pattern.startsWith("^")) {
            pattern = pattern.substring(1);
        }
        if (pattern.endsWith("$")) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        return pattern;
    }
}