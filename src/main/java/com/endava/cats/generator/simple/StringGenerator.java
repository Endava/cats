package com.endava.cats.generator.simple;

import com.endava.cats.util.CatsUtil;
import com.github.curiousoddman.rgxgen.RgxGen;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.cornutum.regexpgen.RandomGen;
import org.cornutum.regexpgen.RegExpGen;
import org.cornutum.regexpgen.js.Provider;
import org.cornutum.regexpgen.random.RandomBoundsGen;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Generates strings based on different criteria.
 */
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

    private static final int MAX_ATTEMPTS_GENERATE = 5;

    private static final String ALPHANUMERIC_VALUE = "CatsIsCool";

    private static final String EMPTY_PATTERN = "(\\(\\^\\$\\)\\|)|(\\^\\$\\)\\|)|(\\(\\^\\$\\|\\))|(\\(\\|\\^\\$\\))|(\\(\\^\\$\\))";

    /**
     * Represents an empty string.
     */
    public static final String EMPTY = "";

    /**
     * List of random content types that is expected to not be supported via API requests.
     */
    private static final List<String> UNSUPPORTED_MEDIA_TYPES = Arrays.asList("application/java-archive",
            "application/javascript",
            "application/octet-stream",
            "application/ogg",
            "application/pdf",
            "application/xhtml+xml",
            "application/x-shockwave-flash",
            "application/ld+json",
            "application/xml",
            "application/zip",
            "application/x-www-form-urlencoded",
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/tiff",
            "image/vnd.microsoft.icon",
            "image/x-icon",
            "image/vnd.djvu",
            "image/svg+xml",
            "multipart/mixed; boundary=cats",
            "multipart/alternative; boundary=cats",
            "multipart/related; boundary=cats",
            "multipart/form-data; boundary=cats",
            "text/css",
            "text/csv",
            "text/html",
            "text/javascript",
            "text/plain",
            "text/xml");

    private static final RandomGen REGEXP_RANDOM_GEN = new RandomBoundsGen();
    private static final org.cornutum.regexpgen.Provider REGEXPGEN_PROVIDER = Provider.forEcmaScript();

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(StringGenerator.class);
    public static final String CASE_INSENSITIVE = "(?i)";

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
        if (length == 0) {
            return EMPTY;
        }

        StringBuilder initialValue = new StringBuilder(StringGenerator.sanitize(generate(cleanPattern(regex), length, length)));

        if (initialValue.isEmpty()) {
            return EMPTY;
        }

        if (initialValue.length() != length) {
            int startingAt = initialValue.length() - 1;
            String toRepeat = initialValue.substring(startingAt);
            do {
                initialValue.append(toRepeat);
            } while (initialValue.length() < length);
        }

        return initialValue.substring(0, length);
    }

    /**
     * This method generates a random string according to the given input.
     * If the pattern already has length information the min/max will be ignored.
     * <p>
     * It tries to generate a valid value using 3 types of generators in a fallback manner.
     *
     * @param pattern the regex pattern
     * @param min     min length of the generated string
     * @param max     max length of the generated string
     * @return a random string corresponding to the given pattern and min, max restrictions
     */
    public static String generate(String pattern, int min, int max) {
        LOGGER.debug("Generate for pattern {} min {} max {}", pattern, min, max);
        pattern = cleanPattern(pattern);

        GeneratorParams generatorParams = new GeneratorParams(pattern, min, max);

        String generatedWithRgxGenerator = callGenerateTwice(StringGenerator::generateUsingRgxGenerator, generatorParams);
        if (generatedWithRgxGenerator != null) {
            return generatedWithRgxGenerator;
        }

        String generatedUsingCatsRegexGenerator = callGenerateTwice(StringGenerator::generateUsingCatsRegexGenerator, generatorParams);
        if (generatedUsingCatsRegexGenerator != null) {
            return generatedUsingCatsRegexGenerator;
        }

        String generateUsingRegexpGen = callGenerateTwice(StringGenerator::generateUsingRegexpGen, generatorParams);
        if (generateUsingRegexpGen != null) {
            return generateUsingRegexpGen;
        }

        throw new IllegalArgumentException("Could not generate a string for pattern " + pattern + " with min " + min + " and max " + max);
    }

    public static String callGenerateTwice(Function<GeneratorParams, String> generator, GeneratorParams generatorParams) {
        try {
            String initialVersion = generator.apply(generatorParams);
            if (initialVersion.matches(generatorParams.pattern)) {
                LOGGER.info("Generated value " + initialVersion + " matched " + generatorParams.pattern);
                return initialVersion;
            }
        } catch (Exception e) {
            LOGGER.debug("Generator {} failed #atempt 1", generator.getClass().getSimpleName());
        }

        try {
            String secondVersion = generator.apply(new GeneratorParams(removeLookaheadAssertions(generatorParams.pattern), generatorParams.min, generatorParams.max));
            if (secondVersion.matches(generatorParams.pattern)) {
                LOGGER.info("Generated value with lookaheads removed " + secondVersion + " matched " + generatorParams.pattern);
                return secondVersion;
            }
        } catch (Exception e) {
            LOGGER.debug("Generator {} failed #atempt 2", generator.getClass().getSimpleName());
        }
        return null; // or handle this case appropriately
    }


    public static String cleanPattern(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return ALPHANUMERIC_PLUS;
        }

        if (pattern.matches(".\\^.*")) {
            pattern = pattern.substring(1);
        }
        if (pattern.endsWith("$/")) {
            pattern = StringUtils.removeEnd(pattern, "/");
        }
        if (pattern.endsWith("$")) {
            pattern = StringUtils.removeEnd(pattern, "$");
        }
        if (pattern.startsWith("/^")) {
            pattern = StringUtils.removeStart(pattern, "/");
        }
        pattern = pattern.replaceAll(EMPTY_PATTERN, EMPTY);
        pattern = pattern.replace(CASE_INSENSITIVE, EMPTY);

        return pattern;
    }

    private static String generateUsingRegexpGen(GeneratorParams generatorParams) {
        String pattern = generatorParams.pattern;
        int min = generatorParams.min;
        int max = generatorParams.max;

        RegExpGen generator = REGEXPGEN_PROVIDER.matchingExact(pattern);

        for (int i = 0; i < MAX_ATTEMPTS_GENERATE; i++) {
            if (min == max) {
                min = 0;
            }
            String generated = generator.generate(REGEXP_RANDOM_GEN, min, max);

            if (generated.matches(pattern)) {
                LOGGER.debug("Generated using REGEXP {} matches {}", generated, pattern);
                return generated;
            }
        }

        LOGGER.debug("Returning alphanumeric random string using REGEXP");
        return REGEXPGEN_PROVIDER.matchingExact(ALPHANUMERIC_PLUS).generate(REGEXP_RANDOM_GEN, min, max);
    }

    private static String generateUsingCatsRegexGenerator(GeneratorParams generatorParams) {
        String pattern = generatorParams.pattern;
        int min = generatorParams.min;
        int max = generatorParams.max;

        for (int i = 0; i < MAX_ATTEMPTS_GENERATE; i++) {
            Pattern compiledPattern = Pattern.compile(pattern);
            String secondVersionBase = RegexGenerator.generate(compiledPattern, EMPTY, min, max);

            if (secondVersionBase.matches(pattern)) {
                LOGGER.debug("Generated using CATS generator {} and matches {}", secondVersionBase, pattern);
                return secondVersionBase;
            }
            String generatedString = composeString(secondVersionBase, min, max);

            if (generatedString.matches(pattern)) {
                LOGGER.debug("Generated using CATS generator {} and matches {}", generatedString, pattern);
                return generatedString;
            }
        }
        throw new IllegalStateException("Could not generate regex ");
    }

    private static String generateUsingRgxGenerator(GeneratorParams generatorParams) {
        int attempts = 0;
        String generatedValue;
        String pattern = generatorParams.pattern;
        int min = generatorParams.min;
        int max = generatorParams.max;

        try {
            do {
                generatedValue = new RgxGen(pattern).generate();
                if ((hasLengthInline(pattern) || isSetOfAlternatives(pattern) || (min <= 0 && max <= 0)) && generatedValue.matches(pattern)) {
                    return generatedValue;
                }
                generatedValue = composeString(generatedValue, min, max);
                attempts++;
            } while (attempts < MAX_ATTEMPTS_GENERATE && !generatedValue.matches(pattern));
        } catch (Exception e) {
            LOGGER.debug("RGX generator failed, returning empty.", e);
            return ALPHANUMERIC_VALUE;
        }
        LOGGER.debug("Generated using RGX {}", generatedValue);
        return generatedValue;
    }

    private static boolean isSetOfAlternatives(String regex) {
        try {
            String[] alternatives = regex.split("\\|", -1);

            if (alternatives.length > 1) {
                for (String alternative : alternatives) {
                    Pattern.compile(alternative);
                }
                return true;
            } else {
                return false;
            }
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static boolean hasLengthInline(String pattern) {
        Pattern groupPattern = Pattern.compile("(\\^)?(\\[[^]]*]\\{\\d+}|\\(\\[[^]]*]\\{\\d+}\\)\\?)*(\\$)?");
        Matcher groupMatcher = groupPattern.matcher(pattern);

        return groupMatcher.matches();
    }

    private static String composeString(String initial, int min, int max) {
        if (min <= 0 && max <= 0) {
            return initial;
        }
        String trimmed = initial.trim() + (initial.isEmpty() ? "a" : initial.charAt(0));
        if (trimmed.length() < min) {
            return composeString(trimmed + trimmed, min, max);
        } else if (trimmed.length() > max) {
            int random = max == min ? 0 : CatsUtil.random().nextInt(max - min);
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
            minLength = Integer.MAX_VALUE / 100;
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
        if (schema.getEnum() != null) {
            String value = String.valueOf(schema.getEnum().get(0));
            return RandomStringUtils.randomAlphanumeric(Math.max(1, value.length()));
        }

        int minLength = schema.getMinLength() != null ? schema.getMinLength() - 1 : 0;

        if (minLength <= 0) {
            return EMPTY;
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
            int codePoint = CatsUtil.random().nextInt(Character.MAX_CODE_POINT + 1);
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
        int minLength = property.getMinLength() != null ? property.getMinLength() : 1;
        int maxLength = property.getMaxLength() != null ? property.getMaxLength() : 50;
        String pattern = StringUtils.isNotBlank(property.getPattern()) ? property.getPattern() : StringGenerator.ALPHANUMERIC_PLUS;
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
                .replaceAll("(^[\\p{Z}\\p{C}\\p{So}\\p{M}\\p{Sk}]+)|([\\p{Z}\\p{C}\\p{So}\\p{M}\\p{Sk}]+$)", EMPTY)
                .replaceAll("[\\p{C}\\p{So}\\p{M}\\p{Sk}\r\n]+", EMPTY);
    }

    /**
     * Returns a list of random content types that is expected to not be supported via API requests.
     *
     * @return a list of random content types to be used for fuzzing
     */
    public static List<String> getUnsupportedMediaTypes() {
        return UNSUPPORTED_MEDIA_TYPES;
    }

    /**
     * Removes lookaheads which might cause current generators to fail.
     *
     * @param regex the given regex
     * @return a regex with lookaheads removed
     */
    public static String removeLookaheadAssertions(String regex) {
        // Replace negative lookahead (?!) with an empty string
        regex = regex.replaceAll("\\(\\?!.*?\\)", "");
        // Replace positive lookahead (?=) with an empty string
        regex = regex.replaceAll("\\(\\?=.+?\\)", "");
        return regex;
    }

    /**
     * @param pattern
     * @param min
     * @param max
     */
    public record GeneratorParams(String pattern, int min, int max) {
    }
}
