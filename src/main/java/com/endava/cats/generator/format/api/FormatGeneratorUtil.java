package com.endava.cats.generator.format.api;

import com.endava.cats.util.CatsRandom;

/**
 * Utility class for common format generation patterns.
 * Reduces code duplication across format generators.
 */
public final class FormatGeneratorUtil {

    private FormatGeneratorUtil() {
        // Utility class
    }

    /**
     * Generates a random uppercase letter (A-Z).
     *
     * @return random uppercase letter
     */
    public static char randomLetter() {
        return (char) ('A' + CatsRandom.instance().nextInt(26));
    }

    /**
     * Generates a random digit (0-9).
     *
     * @return random digit
     */
    public static int randomDigit() {
        return CatsRandom.instance().nextInt(10);
    }

    /**
     * Generates a random number in the specified range [min, max).
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (exclusive)
     * @return random number in range
     */
    public static int randomInRange(int min, int max) {
        return CatsRandom.instance().nextInt(max - min) + min;
    }

    /**
     * Generates a string of random uppercase letters.
     *
     * @param length number of letters
     * @return string of random letters
     */
    public static String randomLetters(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(randomLetter());
        }
        return sb.toString();
    }

    /**
     * Generates a string of random digits.
     *
     * @param length number of digits
     * @return string of random digits
     */
    public static String randomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(randomDigit());
        }
        return sb.toString();
    }

    /**
     * Generates a random alphanumeric string.
     *
     * @param length number of characters
     * @return string of random alphanumeric characters
     */
    public static String randomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            if (CatsRandom.instance().nextBoolean()) {
                sb.append(randomDigit());
            } else {
                sb.append(randomLetter());
            }
        }
        return sb.toString();
    }

    /**
     * Generates a formatted string by replacing placeholders.
     * # = random digit (0-9)
     * A = random letter (A-Z)
     * X = random alphanumeric
     *
     * @param pattern pattern with placeholders
     * @return formatted string
     */
    public static String generateFromPattern(String pattern) {
        StringBuilder result = new StringBuilder(pattern.length());
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '#' -> result.append(randomDigit());
                case 'A' -> result.append(randomLetter());
                case 'X' -> result.append(CatsRandom.instance().nextBoolean() ? randomDigit() : randomLetter());
                default -> result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Generates a random number with specified number of digits.
     *
     * @param digits number of digits
     * @return random number
     */
    public static int randomNumber(int digits) {
        int min = (int) Math.pow(10, digits - 1d);
        int max = (int) Math.pow(10, digits);
        return randomInRange(min, max);
    }
}
