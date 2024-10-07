package com.endava.cats.generator.simple;

import com.endava.cats.util.CatsModelUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.endava.cats.util.CatsModelUtils.isEmail;
import static com.endava.cats.util.CatsModelUtils.isPassword;
import static com.endava.cats.util.CatsModelUtils.isUri;

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
    private static final int MAX_ATTEMPTS_GENERATE = 5;
    private static final String ALPHANUMERIC_VALUE = "CatsIsCool";

    private static final Pattern HAS_LENGTH_PATTERN = Pattern.compile("(\\*|\\+|\\?|\\{\\d+(,\\d*)?\\})");
    private static final Pattern SINGLE_CHAR_PATTERN = Pattern.compile("^\\[[^\\]]+\\]$");
    private static final Pattern LENGTH_INLINE_PATTERN = Pattern.compile("(\\^)?(\\[[^]]*]\\{\\d+}|\\(\\[[^]]*]\\{\\d+}\\)\\?)*(\\$)?");

    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String[] DOMAINS = {"example", "cats", "google", "yahoo"};
    private static final String[] TLDS = {".com", ".net", ".org", ".io"};
    private static final String[] URI_SCHEMES = {"http", "https", "ftp", "file"};
    private static final int TIMEOUT_MS = 200;

    private static final List<String> SIMPLE_REGEXES = List.of("[A-Z]+", "[a-z]+", "[A-Za-z]+", "[0-9]+", "[A-Za-z0-9]+", "[A-Z0-9]+", "[a-z0-9]+", "\\w+", "[A-Za-z0-9_\\-#!]");

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
    public static final int DEFAULT_MAX_WHEN_NOT_PRESENT = 256;
    public static final int DEFAULT_MIN_WHEN_NOT_PRESENT = 1;

    private StringGenerator() {
        //ntd
    }

    /**
     * Generates a random alphanumeric string prefixed with string "fuzz"
     *
     * @return a random alphanumeric string
     */
    public static String generateRandomString() {
        return FUZZ + RandomStringUtils.secure().nextAlphabetic(4);
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
    public static String generateExactLength(Schema<?> schema, String regex, int length) {
        if (length <= 0) {
            return EMPTY;
        }

        String stringFromComplexRegex = generateComplexRegex(schema, length);
        if (stringFromComplexRegex != null) {
            return stringFromComplexRegex;
        }

        StringBuilder initialValue = new StringBuilder(StringGenerator.sanitize(generate(regex, length, length)));

        if (initialValue.isEmpty()) {
            return EMPTY;
        }

        if (initialValue.length() < length) {
            char lastChar = initialValue.charAt(initialValue.length() - 1);
            int charsToAdd = length - initialValue.length();
            initialValue.append(String.valueOf(lastChar).repeat(charsToAdd));
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
        String cleanedPattern = RegexCleaner.cleanPattern(pattern);
        String flattenedPattern = RegexFlattener.flattenRegex(cleanedPattern);

        List<Supplier<Optional<String>>> attempts = List.of(
                () -> generateString(pattern, min, max, cleanedPattern, flattenedPattern),
                () -> generateString(cleanedPattern, min, max, cleanedPattern, flattenedPattern),
                () -> generateString(cleanedPattern, min, max, cleanedPattern, cleanedPattern),
                () -> generateString(flattenedPattern, min, max, cleanedPattern, flattenedPattern),
                () -> generateString(flattenedPattern, min, max, flattenedPattern, flattenedPattern)
        );

        return attempts.stream()
                .flatMap(attempt -> attempt.get().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Could not generate a string for pattern %s with min %d and max %d", pattern, min, max)
                ));
    }

    /**
     * Generates a random string based on the given pattern and min/max restrictions.
     * It tries to generate a valid value using 3 types of generators in a fallback manner.
     *
     * @param pattern          the regex pattern
     * @param min              min length of the generated string
     * @param max              max length of the generated string
     * @param cleanedPattern   the cleaned pattern
     * @param flattenedPattern the flattened pattern
     * @return a random string corresponding to the given pattern and min, max restrictions
     */
    private static Optional<String> generateString(String pattern, int min, int max, String cleanedPattern, String flattenedPattern) {
        String valueBasedOnSimpleRegexes = tryGenerateWithSimpleRegexes(pattern, min, max, cleanedPattern);

        if (valueBasedOnSimpleRegexes != null) {
            return Optional.of(valueBasedOnSimpleRegexes);
        }

        GeneratorParams generatorParams = new GeneratorParams(flattenedPattern, min, max, cleanedPattern);

        Optional<String> generatedWithInitialMinMax = callGeneratorsInOrder(generatorParams);
        if (generatedWithInitialMinMax.isPresent()) {
            return generatedWithInitialMinMax;
        }

        if (min == -1 && max == -1) {
            GeneratorParams generatorParamsWithMinMax = new GeneratorParams(flattenedPattern, 1, 300, cleanedPattern);

            Optional<String> generatedWithAdjustedMinMax = callGeneratorsInOrder(generatorParamsWithMinMax);
            if (generatedWithAdjustedMinMax.isPresent()) {
                return generatedWithAdjustedMinMax;
            }
        }

        return Optional.empty();
    }

    /**
     * Calls the generators in a specific order and returns the first successful result.
     *
     * @param generatorParams the generator parameters
     * @return the generated string
     */
    private static Optional<String> callGeneratorsInOrder(GeneratorParams generatorParams) {
        String rgxGeneratedWithMinMax = callGenerateTwice(StringGenerator::generateUsingRgxGenerator, generatorParams);
        if (rgxGeneratedWithMinMax != null) {
            return Optional.of(rgxGeneratedWithMinMax);
        }

        String generatedWithCatsRegexGenerator = callGenerateTwice(StringGenerator::generateUsingCatsRegexGenerator, generatorParams);
        if (generatedWithCatsRegexGenerator != null) {
            return Optional.of(generatedWithCatsRegexGenerator);
        }

        String generatedWithRegexpGen = callGenerateTwice(StringGenerator::generateUsingRegexpGen, generatorParams);
        if (generatedWithRegexpGen != null) {
            return Optional.of(generatedWithRegexpGen);
        }
        return Optional.empty();
    }

    public static String tryGenerateWithSimpleRegexes(String originalPattern, int min, int max, String cleanedPattern) {
        for (String simpleRegex : SIMPLE_REGEXES) {
            String generated = generateUsingRgxGenerator(new GeneratorParams(simpleRegex, min, max, simpleRegex));
            if (generated.matches(originalPattern) || generated.matches(cleanedPattern)) {
                LOGGER.debug("Generated value {} with simple regex matches original pattern {}", generated, originalPattern);
                return generated;
            }
        }
        return null;
    }

    public static String callGenerateTwice(Function<GeneratorParams, String> generator, GeneratorParams generatorParams) {
        try {
            String initialVersion = generator.apply(generatorParams);
            if (initialVersion.matches(generatorParams.originalPattern())) {
                LOGGER.debug("Generated value " + initialVersion + " matched " + generatorParams.originalPattern());
                return initialVersion;
            }
        } catch (Exception e) {
            LOGGER.debug("Generator {} failed #atempt 1", generator.getClass().getSimpleName());
        }
        try {
            String patternWithLookaheadsRemoved = removeLookaheadAssertions(generatorParams.cleanedPattern());
            LOGGER.debug("Pattern with lookaheads removed {}", patternWithLookaheadsRemoved);

            String secondVersion = generator.apply(new GeneratorParams(patternWithLookaheadsRemoved, generatorParams.min, generatorParams.max, generatorParams.originalPattern()));
            if (secondVersion.matches(generatorParams.originalPattern())) {
                LOGGER.debug("Generated value with lookaheads removed " + secondVersion + " matched " + generatorParams.originalPattern());
                return secondVersion;
            }
        } catch (Exception e) {
            LOGGER.debug("Generator {} failed #atempt 2", generator.getClass().getSimpleName());
        }
        return null;
    }


    private static String generateUsingRegexpGen(GeneratorParams generatorParams) {
        String pattern = generatorParams.cleanedPattern();
        String originalPattern = generatorParams.originalPattern();
        int min = generatorParams.min;
        int max = generatorParams.max;

        RegExpGen generator = REGEXPGEN_PROVIDER.matchingExact(pattern);

        for (int i = 0; i < MAX_ATTEMPTS_GENERATE; i++) {
            if (min == max) {
                min = 0;
            }
            String generated = generator.generate(REGEXP_RANDOM_GEN, min, max);

            if (generated.matches(originalPattern)) {
                LOGGER.debug("Generated using REGEXP {} matches {}", generated, pattern);
                return generated;
            }
        }

        LOGGER.debug("Returning alphanumeric random string using REGEXP");
        return REGEXPGEN_PROVIDER.matchingExact(ALPHANUMERIC_PLUS).generate(REGEXP_RANDOM_GEN, min, max);
    }

    private static String generateUsingCatsRegexGenerator(GeneratorParams generatorParams) {
        String pattern = generatorParams.cleanedPattern();
        String originalPattern = generatorParams.originalPattern();
        int min = generatorParams.min;
        int max = generatorParams.max;

        for (int i = 0; i < MAX_ATTEMPTS_GENERATE; i++) {
            Pattern compiledPattern = Pattern.compile(pattern);
            String secondVersionBase = generateWithTimeout(compiledPattern, min, max);

            if (secondVersionBase.matches(originalPattern)) {
                LOGGER.debug("Generated using CATS generator {} and matches {}", secondVersionBase, pattern);
                return secondVersionBase;
            }
            String generatedString = composeString(secondVersionBase, min, max);

            if (generatedString.matches(originalPattern)) {
                LOGGER.debug("Generated using CATS generator {} and matches {}", generatedString, pattern);
                return generatedString;
            }
        }
        throw new IllegalStateException("Could not generate regex ");
    }

    public static String generateWithTimeout(Pattern compiledPattern, int min, int max) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                return RegexGenerator.generate(compiledPattern, EMPTY, min, max);
            } catch (Exception e) {
                LOGGER.trace("Error in RegexGenerator.generate()", e);
                return EMPTY;
            }
        });

        try {
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            LOGGER.trace("RegexGenerator.generate() timed out after " + TIMEOUT_MS + " ms {}", e.getMessage());
            throw new IllegalStateException("Could not generate regex");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("RegexGenerator.generate() interrupted {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.trace("Error in RegexGenerator.generate(): {}", e.getMessage());
        }

        return EMPTY;
    }

    private static String generateUsingRgxGenerator(GeneratorParams generatorParams) {
        int attempts = 0;
        String generatedValue;
        String pattern = generatorParams.cleanedPattern();
        String originalPattern = generatorParams.originalPattern();
        int min = generatorParams.min;
        int max = generatorParams.max;

        try {
            RgxGen rgxGen = new RgxGen(pattern);
            do {
                generatedValue = rgxGen.generate();
                if (matchesLength(pattern, min, max, generatedValue) && generatedValue.matches(originalPattern)) {
                    return generatedValue;
                }
                generatedValue = composeString(generatedValue, min, max);
                attempts++;
            } while (attempts < MAX_ATTEMPTS_GENERATE && !generatedValue.matches(originalPattern));
        } catch (Exception e) {
            LOGGER.debug("RGX generator failed, returning empty.", e);
            return ALPHANUMERIC_VALUE;
        }
        LOGGER.debug("Generated using RGX {}", generatedValue);
        return generatedValue;
    }

    private static boolean matchesLength(String pattern, int min, int max, String generatedValue) {
        return hasLengthInline(pattern) || isSetOfAlternatives(pattern) || (min <= 0 && max <= 0) || hasLengthBetween(generatedValue, min, max);
    }

    private static boolean hasLengthBetween(String string, int min, int max) {
        return string.length() >= min && string.length() <= max;
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
        Matcher groupMatcher = LENGTH_INLINE_PATTERN.matcher(pattern);

        return groupMatcher.matches();
    }

    static boolean hasLength(String pattern) {
        Matcher groupMatcher = HAS_LENGTH_PATTERN.matcher(pattern);

        return groupMatcher.find();
    }

    static boolean isSingleChar(String pattern) {
        Matcher groupMatcher = SINGLE_CHAR_PATTERN.matcher(pattern);

        return groupMatcher.find();
    }

    public static String composeString(String initial, int min, int max) {
        if (min <= 0 && max <= 0) {
            return initial;
        }

        if (max < min) {
            max = min;
        }

        StringBuilder trimmed = new StringBuilder(StringUtils.isBlank(initial) ? "a" : initial.trim());

        while (trimmed.length() < min) {
            int substringPosition = Math.clamp(trimmed.length() - 1L, 0, Math.min(trimmed.length() - 1, 4));
            if (substringPosition > 0 && substringPosition < trimmed.length()) {
                trimmed.append(trimmed.substring(substringPosition));
            } else {
                trimmed.append(trimmed);
            }
        }

        if (trimmed.length() > max) {
            int randomReduction = max == min ? 0 : CatsUtil.random().nextInt(max - min + 1);
            int newLength = max - randomReduction;
            trimmed = new StringBuilder(trimmed.substring(0, newLength));
        }

        return trimmed.toString();
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
        long maxLength = getRightBoundaryLength(schema);
        return generate(ALPHANUMERIC_PLUS, (int) maxLength, (int) maxLength);
    }

    /**
     * Generates a string larger than schema's max length. If schema has no maxLength it will default to DEFAULT_MAX_LENGTH.
     *
     * @param schema the OpenAPI schema
     * @return a string larger than Schema's maxLength
     */
    public static long getRightBoundaryLength(Schema<?> schema) {
        long maxLength = schema.getMaxLength() != null ? schema.getMaxLength().longValue() + 10 : DEFAULT_MAX_LENGTH;

        if (maxLength > Integer.MAX_VALUE) {
            maxLength = Integer.MAX_VALUE / 100;
        }
        return maxLength;
    }

    /**
     * Generates a string smaller than schema's min length. If schema has no minLength it will default to 0.
     *
     * @param schema the OpenAPI schema
     * @return a string smaller than Schema's minLength
     */
    public static String generateLeftBoundString(Schema<?> schema) {
        if (schema.getEnum() != null) {
            String value = String.valueOf(schema.getEnum().getFirst());
            return RandomStringUtils.secure().nextAlphanumeric(Math.max(DEFAULT_MIN_WHEN_NOT_PRESENT, value.length()));
        }

        int minLength = schema.getMinLength() != null ? schema.getMinLength() - 1 : 0;

        if (minLength <= 0) {
            return EMPTY;
        }

        return generate(ALPHANUMERIC_PLUS, minLength, minLength);
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
            return String.valueOf(property.getEnum().getFirst());
        }
        int minLength = property.getMinLength() != null ? property.getMinLength() : DEFAULT_MIN_WHEN_NOT_PRESENT;
        int maxLength = property.getMaxLength() != null ? property.getMaxLength() : DEFAULT_MAX_WHEN_NOT_PRESENT;
        String pattern = StringUtils.isNotBlank(property.getPattern()) ? property.getPattern() : StringGenerator.ALPHANUMERIC_PLUS;
        if (maxLength < minLength) {
            maxLength = minLength;
        }

        String complexRegexGenerated = generateComplexRegex(property, Math.max(1, maxLength));
        if (complexRegexGenerated != null) {
            return complexRegexGenerated;
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
        regex = regex.replaceAll("\\(\\?=([^)]*)\\)", "($1)");
        regex = regex.replaceAll("\\(\\?!([^)]*)\\)", "");
        regex = regex.replaceAll("\\(\\?<=([^)]*)\\)", "($1)");
        regex = regex.replaceAll("\\(\\?<!([^)]*)\\)", "(^$1)");

        return regex;
    }

    public static String generateFixedLengthEmail(int length) {
        String domain = DOMAINS[CatsUtil.random().nextInt(DOMAINS.length)];
        String tld = TLDS[CatsUtil.random().nextInt(TLDS.length)];

        int localPartLength = length - domain.length() - tld.length() - 1; // -1 for '@'

        StringBuilder localPart = new StringBuilder();
        for (int i = 0; i < localPartLength; i++) {
            localPart.append(ALPHANUMERIC.charAt(CatsUtil.random().nextInt(ALPHANUMERIC.length())));
        }

        return localPart + "@" + domain + tld;
    }

    public static String generateFixedLengthUri(int length) {
        String scheme = URI_SCHEMES[CatsUtil.random().nextInt(URI_SCHEMES.length)];

        String domain = DOMAINS[CatsUtil.random().nextInt(DOMAINS.length)];
        String tld = TLDS[CatsUtil.random().nextInt(TLDS.length)];

        String fixedPart = scheme + "://" + domain + tld;

        int pathLength = length - fixedPart.length();
        if (pathLength <= 0) {
            return fixedPart.substring(0, length);
        }

        StringBuilder path = new StringBuilder();
        path.append("/");
        for (int i = 0; i < pathLength - 1; i++) {
            path.append(ALPHANUMERIC.charAt(CatsUtil.random().nextInt(ALPHANUMERIC.length())));
        }

        return fixedPart + path;
    }

    /**
     * There are complex regexes which will fail to generate a string of a given length, especially for a fixed and large length.
     * This is particularly true for email addresses and URIs where patterns can be quite complex.
     * Sometimes, for large generated strings, the match of the generated string against the given regex will fail with StackOverflowError.
     * <p>
     * This method tries to generate a string of a given length for such complex regexes. It only supports URIs and emails for now.
     *
     * @param schema the schema
     * @param length the length
     * @return a string of given length matching patterns schema
     */
    private static String generateComplexRegex(Schema<?> schema, int length) {
        if (StringUtils.isBlank(schema.getPattern())) {
            return null;
        }

        String lowerCaseFieldName = Optional.ofNullable(schema.getExtensions()).orElse(Collections.emptyMap()).getOrDefault(CatsModelUtils.X_CATS_FIELD_NAME, "").toString().toLowerCase(Locale.ROOT);
        String pattern = schema.getPattern();
        LOGGER.debug("Checking if the field {} with pattern {} is a complex regex", lowerCaseFieldName, pattern);

        if (isUri(pattern, lowerCaseFieldName)) {
            return generateFixedLengthUri(length);
        }
        if (isEmail(pattern, lowerCaseFieldName)) {
            return generateFixedLengthEmail(length);
        }
        if (isPassword(pattern, lowerCaseFieldName)) {
            return "catsISC00l#" + RandomStringUtils.secure().nextPrint(length - 11);
        }

        return null;
    }

    /**
     * A record that holds the parameters for the string generator.
     *
     * @param cleanedPattern  the pattern to check
     * @param min             the minimum length
     * @param max             the maximum length
     * @param originalPattern the original pattern
     */
    public record GeneratorParams(String cleanedPattern, int min, int max, String originalPattern) {
        /**
         * Instantiates a new Generator params.
         *
         * @param cleanedPattern  the pattern
         * @param min             the min
         * @param max             the max
         * @param originalPattern the original pattern
         */
        public GeneratorParams(String cleanedPattern, int min, int max, String originalPattern) {
            this.min = min;
            this.max = max;
            this.originalPattern = inlineLengthIfNeeded(originalPattern, min, max);
            this.cleanedPattern = inlineLengthIfNeeded(cleanedPattern, min, max);
        }

        private String inlineLengthIfNeeded(String pattern, int min, int max) {
            if (isSingleChar(pattern) && (min > 0 || max > 0)) {
                return pattern + "{" + min + "," + max + "}";
            }
            return pattern;
        }
    }
}
