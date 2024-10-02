package com.endava.cats.generator.simple;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public abstract class RegexCleaner {
    private static final String EMPTY_PATTERN = "(\\(\\^\\$\\)\\|)|(\\^\\$\\)\\|)|(\\(\\^\\$\\|\\))|(\\(\\|\\^\\$\\))|(\\(\\^\\$\\))";
    private static final String ALPHANUMERIC_PLUS = "[a-zA-Z0-9]+";
    private static final String EMPTY = "";
    private static final List<String> WILD_CARDS = List.of(".^");
    private static final String CASE_INSENSITIVE = "(?i)";

    private RegexCleaner() {
        // Utility class
    }

    public static String cleanPattern(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return ALPHANUMERIC_PLUS;
        }
        if (WILD_CARDS.contains(pattern)) {
            return ALPHANUMERIC_PLUS;
        }
        if (pattern.startsWith("/") && pattern.endsWith("/i")) {
            pattern = pattern.substring(1, pattern.length() - 2);
        }
        if (pattern.matches("(?!\\().?(?<!\\[)\\^.*")) {
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
        pattern = removeMisplacedDollarSigns(pattern);

        if (pattern.startsWith("|")) {
            pattern = pattern.substring(1);
        }

        return pattern;
    }

    private static String removeMisplacedDollarSigns(String regex) {
        StringBuilder result = new StringBuilder(regex.length());
        boolean inCharClass = false;
        int parenDepth = 0;
        boolean escape = false;

        for (int i = 0; i < regex.length(); i++) {
            char currentChar = regex.charAt(i);

            if (escape) {
                result.append(currentChar);
                escape = false;
                continue;
            }

            switch (currentChar) {
                case '\\':
                    result.append(currentChar);
                    escape = true;
                    break;
                case '[':
                    inCharClass = true;
                    result.append(currentChar);
                    break;
                case ']':
                    inCharClass = false;
                    result.append(currentChar);
                    break;
                case '(':
                    parenDepth++;
                    result.append(currentChar);
                    break;
                case ')':
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                    result.append(currentChar);
                    break;
                case '$':
                    if (shouldAppendDollar(i, regex.length(), inCharClass, parenDepth)) {
                        result.append(currentChar);
                    }
                    break;
                default:
                    result.append(currentChar);
            }
        }

        return result.toString();
    }

    private static boolean shouldAppendDollar(int currentIndex, int length, boolean inCharClass, int parenDepth) {
        return currentIndex == length - 1 || inCharClass || parenDepth > 0;
    }
}
