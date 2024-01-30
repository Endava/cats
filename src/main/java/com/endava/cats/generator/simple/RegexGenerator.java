package com.endava.cats.generator.simple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates strings based on regexes.
 */
public class RegexGenerator {
    /**
     * Represents the string "DEFAULT_CATS"
     */
    public static final String DEFAULT = "DEFAULT_CATS";
    private static final int MAX_ITERATIONS = 10;

    private RegexGenerator() {
        //ntd
    }

    /**
     * Generates a string based on the specified pattern, prefix, minimum, and maximum length.
     *
     * @param pattern The pattern to generate the string.
     * @param prefix  The prefix to prepend to the generated string.
     * @param min     The minimum length of the generated string.
     * @param max     The maximum length of the generated string.
     * @return The generated string based on the given pattern, prefix, minimum, and maximum length.
     * If the generated string is equal to the default value and the number of iterations
     * is within the maximum limit, it retries generating the string.
     */
    public static String generate(Pattern pattern, String prefix, int min, int max) {
        String result = generateString(pattern, prefix, min, max);
        int interations = 0;
        while (result.equalsIgnoreCase(DEFAULT) && interations < MAX_ITERATIONS) {
            result = generateString(pattern, prefix, min, max);
            interations++;
        }

        return result;
    }

    private static String generateString(Pattern pattern, String prefix, int min, int max) {
        if (min <= 0 && max >= 0 && pattern.matcher(prefix).matches())
            return prefix;
        if (max <= 0) {
            return DEFAULT;
        }
        List<Character> candidates = new ArrayList<>();
        generateCandidates(candidates, pattern, prefix);
        Collections.shuffle(candidates);
        return verifyAndReturn(pattern, prefix, min, max, candidates);
    }

    private static void generateCandidates(List<Character> candidates, Pattern p, String prefix) {
        for (char c = 0; c <= 255; c++) {
            Matcher m = p.matcher(prefix + c);
            if (m.matches() || m.hitEnd()) {
                candidates.add(c);
            }
        }
    }

    private static String verifyAndReturn(Pattern p, String prefix, int min, int max, List<Character> candidates) {
        for (char candidate : candidates) {
            String match = generateString(p, prefix + candidate, min - 1, max - 1);
            if (match != null) {
                return match;
            }
        }
        return DEFAULT;
    }
}
