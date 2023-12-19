package com.endava.cats.generator.simple;

import com.github.curiousoddman.rgxgen.RgxGen;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.cornutum.regexpgen.RandomGen;
import org.cornutum.regexpgen.RegExpGen;
import org.cornutum.regexpgen.js.Provider;
import org.cornutum.regexpgen.random.RandomBoundsGen;
import org.springframework.util.CollectionUtils;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public class StringGenerator {
    /**
     * Constant use to prefix certain fuzz values.
     */
    public static final String FUZZ = "fuzz";

    /**
     * Default max length when no maxLength is specified for a schema.
     */
    public static final int DEFAULT_MAX_LENGTH = 10000;

    /**
     * Default alphanumeric pattern.
     */
    public static final String ALPHANUMERIC_PLUS = "[a-zA-Z0-9]+";

    /**
     * Default alphanumeric pattern.
     */
    public static final String ALPHANUMERIC = "[a-zA-Z0-9]";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MAX_ATTEMPTS_GENERATE = 10;

    private StringGenerator() {
        //ntd
    }

    /**
     * Generates a random alphanumeric string prefixed with string "fuzz"
     *
     * @return a random alphanumeric string
     */
    public static String generateRandomString() {
        return FUZZ + RandomStringUtils.randomAlphabetic(4);
    }

    /**
     * Repeats the string "fuzz" the number of {@code times}.
     *
     * @param times the number of times to repeat string "fuzz"
     * @return a string of length times * 4
     */
    public static String generateLargeString(int times) {
        return StringUtils.repeat(FUZZ, times);
    }

    /**
     * This method makes sure that the generated size of the string is matching the given length.
     * When patterns have length inside, for example {@code "\\d{6,10}" }, the generated value
     * will have a variable length, in the case of the example between 6 and 10.
     *
     * @param regex  the given  pattern
     * @param length the desired length
     * @return a generated value of exact length provided
     */
    public static String generateExactLength(String regex, int length) {
        StringBuilder initialValue = new StringBuilder(StringGenerator.sanitize(generate(regex, length, length)));

        if (initialValue.length() != length) {
            int startingAt = initialValue.length() - 1;
            String toRepeat = initialValue.substring(startingAt);
            initialValue.append(toRepeat);
            while (initialValue.length() < length) {
                initialValue.append(toRepeat);
            }
        }

        return initialValue.substring(0, length);
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
        String initialVersion = generateUsingRgxGenerator(pattern, min, max);
        if (initialVersion.matches(pattern)) {
            return initialVersion;
        }

        try {
            return generateUsingCatsRegexGenerator(pattern, min, max);
        } catch (Exception e) {
            return generateUsingRegexpGen(pattern, min, max);
        }
    }

    private static String generateUsingRegexpGen(String pattern, int min, int max) {
        RegExpGen generator = Provider.forEcmaScript().matchingExact(pattern);
        RandomGen random = new RandomBoundsGen();

        for (int i = 0; i < MAX_ATTEMPTS_GENERATE; i++) {
            String generated = generator.generate(random, min, max);

            if (generated.matches(pattern) && generated.length() >= min && generated.length() <= max) {
                return generated;
            }
        }

        return Provider.forEcmaScript().matchingExact(ALPHANUMERIC_PLUS).generate(random, min, max);
    }

    private static String generateUsingCatsRegexGenerator(String pattern, int min, int max) {
        for (int i = 0; i < MAX_ATTEMPTS_GENERATE; i++) {
            String secondVersionBase = RegexGenerator.generate(Pattern.compile(pattern), "", 10, 15);
            String generatedString = composeString(secondVersionBase, min, max);

            if (generatedString.matches(pattern) && generatedString.length() >= min && generatedString.length() <= max) {
                return generatedString;
            }
        }
        throw new IllegalStateException("Could not generate regex ");
    }

    private static String generateUsingRgxGenerator(String pattern, int min, int max) {
        try {
            String generatedValue = new RgxGen(pattern).generate();
            if ((pattern.endsWith("}") || pattern.endsWith("}$") && generatedValue.matches(pattern))) {
                return generatedValue;
            }
            return composeString(generatedValue, min, max);
        } catch (Exception e) {
            return "";
        }
    }

    private static String composeString(String initial, int min, int max) {
        if (min == 0 && max == 0) {
            return initial;
        }
        String trimmed = initial.trim().replaceAll("\\p{Z}+", "") + (initial.isEmpty() ? "a" : initial.charAt(0));
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
     * allowed for a char array on most JVMs. If the maxLength is less than
     * {@code Integer.MAX_VALUE - 10}, then the generated value will have maxLength + 10 length. If the Schema has no maxLength
     * defined, it will default to {@code DEFAULT_MAX_LENGTH}.
     *
     * @param schema the associated schema of current fuzzed field
     * @return a random String whose length is bigger than maxLength
     */
    public static String generateRightBoundString(Schema<?> schema) {
        long minLength = getRightBoundaryLength(schema);
        return StringUtils.repeat('a', (int) minLength);
    }

    /**
     * Generates a string larger than schema's max length. If schema has no maxLength it will default to DEFAULT_MAX_LENGTH.
     *
     * @param schema the OpenAPI schema
     * @return a string larger than Schema's maxLength
     */
    public static long getRightBoundaryLength(Schema<?> schema) {
        long minLength = schema.getMaxLength() != null ? schema.getMaxLength().longValue() + 10 : DEFAULT_MAX_LENGTH;

        if (minLength > Integer.MAX_VALUE) {
            minLength = Integer.MAX_VALUE - 2L;
        }
        return minLength;
    }

    /**
     * Generates a string smaller than schema's min length. If schema has no minLength it will default to 0.
     *
     * @param schema the OpenAPI schema
     * @return a string smaller than Schema's minLength
     */
    public static String generateLeftBoundString(Schema<?> schema) {
        int minLength = schema.getMinLength() != null ? schema.getMinLength() - 1 : 0;

        if (minLength <= 0) {
            return "";
        }
        String pattern = ALPHANUMERIC + "{" + (minLength - 1) + "," + minLength + "}";

        return new RgxGen(pattern).generate();
    }

    /**
     * Generates a random unicode string.
     *
     * @return a random unicode string
     */
    public static String generateRandomUnicode() {
        StringBuilder builder = new StringBuilder(UnicodeGenerator.getBadPayload().length() + 1000);
        builder.append(UnicodeGenerator.getBadPayload());

        int count = 1000;
        while (count > 0) {
            int codePoint = RANDOM.nextInt(Character.MAX_CODE_POINT + 1);
            int type = Character.getType(codePoint);

            if (!Character.isDefined(codePoint) || type == Character.PRIVATE_USE || type == Character.SURROGATE || type == Character.UNASSIGNED) {
                continue;
            }

            builder.appendCodePoint(codePoint);
            count--;
        }

        return builder.toString();
    }

    /**
     * Generates a string value with size between  minLength and maxLength.
     * If the field is an enum, it will return the first element in the enum.
     *
     * @param property the OpenAPI schema
     * @return a random string with size between minLength adn maxLength
     */
    public static String generateValueBasedOnMinMax(Schema<?> property) {
        if (!CollectionUtils.isEmpty(property.getEnum())) {
            return String.valueOf(property.getEnum().get(0));
        }
        int minLength = property.getMinLength() != null ? property.getMinLength() : 5;
        int maxLength = property.getMaxLength() != null ? property.getMaxLength() - 1 : 10;
        String pattern = property.getPattern() != null ? property.getPattern() : StringGenerator.ALPHANUMERIC_PLUS;
        if (maxLength < minLength) {
            maxLength = minLength;
        }
        return StringGenerator.generate(pattern, minLength, maxLength);
    }

    /**
     * Sanitizes the given input string by removing special Unicode characters.
     *
     * @param input the given string
     * @return a sanitized version of the given string
     */
    public static String sanitize(String input) {
        return input
                .replaceAll("(^[\\p{Z}\\p{C}\\p{So}\\p{M}\\p{Sk}]+)|([\\p{Z}\\p{C}\\p{So}\\p{M}\\p{Sk}]+$)", "")
                .replaceAll("[\\p{C}\\p{So}\\p{M}\\p{Sk}\r\n]+", "");
    }
}