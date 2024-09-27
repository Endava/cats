package com.endava.cats.generator.simple;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flattens a regex by simplifying character classes, quantifiers, and removing unnecessary parentheses.
 */
public abstract class RegexFlattener {

    private RegexFlattener() {
        // Utility class
    }

    /**
     * Flattens a regex by simplifying character classes and quantifiers.
     *
     * @param regex the regex to flatten
     * @return the flattened regex
     */
    public static String flattenRegex(String regex) {
        regex = simplifyCharacterClasses(regex);
        regex = simplifyQuantifiers(regex);
        regex = removeStartEndAnyChar(regex);
        regex = removeEndOrEmpty(regex);

        return regex;
    }

    private static String removeEndOrEmpty(String regex) {
        if (regex.endsWith("|")) {
            return regex.substring(0, regex.length() - 1);
        }
        if (regex.endsWith("|^")) {
            return regex.substring(0, regex.length() - 2);
        }
        return regex;
    }

    private static String removeStartEndAnyChar(String regex) {
        if (regex.equals(".*")) {
            return regex;
        }

        // Remove leading unescaped '.*' or '*' patterns
        regex = regex.replaceAll("^(?:(?<!\\\\)\\*+\\.*|\\.*(?<!\\\\)\\*+)", "");

        // Remove trailing unescaped '.*' or '*' patterns
        regex = regex.replaceAll("(?:(?<!\\\\)\\*+\\.*|\\.*(?<!\\\\)\\*+)$", "+");

        return regex;
    }

    private static String simplifyCharacterClasses(String regex) {
        String flattenedRegex = regex;

        flattenedRegex = flattenedRegex.replace("[a-zA-Z0-9_]", "\\w");
        flattenedRegex = flattenedRegex.replace("[0-9]", "\\d");
        flattenedRegex = flattenedRegex.replace("[\\s\\t\\r\\n\\f]", "\\s");
        flattenedRegex = flattenedRegex.replace("^\\u0000-\\u00FF", "\\u0100-\\uFFFF");

        flattenedRegex = simplifyNegatedClass(flattenedRegex, "\\d", "\\D");
        flattenedRegex = simplifyNegatedClass(flattenedRegex, "\\w", "\\W");
        flattenedRegex = simplifyNegatedClass(flattenedRegex, "\\s", "\\S");

        return flattenedRegex;
    }

    private static String simplifyNegatedClass(String regex, String positiveClass, String negativeClass) {
        Pattern pattern = Pattern.compile("\\[\\^" + Pattern.quote(positiveClass) + "\\](?![^\\[\\]]*\\])");
        Matcher matcher = pattern.matcher(regex);
        return matcher.replaceAll(negativeClass);
    }

    private static String simplifyQuantifiers(String regex) {
        String flattenedRegex = regex;

        flattenedRegex = flattenedRegex.replace("{0,1}", "?");
        flattenedRegex = flattenedRegex.replace("{1,}", "+");
        flattenedRegex = flattenedRegex.replace("{0,}", "*");

        return flattenedRegex;
    }
}