package com.endava.cats.generator.simple;

/**
 * Flattens a regex by simplifying character classes, quantifiers, and removing unnecessary parentheses.
 */
public abstract class RegexFlattener {


    /**
     * Flattens a regex by simplifying character classes, quantifiers, and removing unnecessary parentheses.
     *
     * @param regex the regex to flatten
     * @return the flattened regex
     */
    public static String flattenRegex(String regex) {
        regex = simplifyCharacterClasses(regex);
        regex = simplifyQuantifiers(regex);
//        regex = useNonCapturingGroups(regex);

        return regex;
    }

    public static String useNonCapturingGroups(String regex) {
        return regex.replaceAll("\\((?!\\?:)(?=[^()]*\\|)", "(?:");
    }

    private static String simplifyCharacterClasses(String regex) {
        regex = regex.replaceAll("\\[a-zA-Z0-9_\\]", "\\\\w");
        regex = regex.replaceAll("\\[0-9\\]", "\\\\d");
        regex = regex.replaceAll("\\[\\s\\t\\r\\n\\f\\]", "\\\\s");

        regex = regex.replaceAll("\\[^\\\\d\\]", "\\\\D");
        regex = regex.replaceAll("\\[^\\\\w\\]", "\\\\W");
        regex = regex.replaceAll("\\[^\\\\s\\]", "\\\\S");

        return regex;
    }

    private static String simplifyQuantifiers(String regex) {
        regex = regex.replaceAll("\\{0,1\\}", "?");
        regex = regex.replaceAll("\\{1,\\}", "+");
        regex = regex.replaceAll("\\{0,\\}", "*");
        return regex;
    }
}
