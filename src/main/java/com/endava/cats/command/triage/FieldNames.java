package com.endava.cats.command.triage;

import com.endava.cats.util.WordUtils;

import java.util.regex.Pattern;

/**
 * Field-path normalization shared by triage analyzers. Triage emits dotted paths with {@code [*]} array segments
 * ({@code items[*].quantity}), the notation accepted by {@code --correlations}.
 */
final class FieldNames {
    private static final Pattern INDEX = Pattern.compile("\\[\\d+]");
    private static final Pattern VALID_PATH = Pattern.compile("[A-Za-z_$][\\w$-]*(?:\\[\\*])?(?:\\.[A-Za-z_$][\\w$-]*(?:\\[\\*])?)*");
    private static final Pattern SIMPLE_YAML_KEY = Pattern.compile("[A-Za-z_$][\\w$.-]*");

    private FieldNames() {
    }

    /**
     * Converts {@code a#b}, {@code a[0].b} or {@code $.a.b} into {@code a.b} / {@code a[*].b}.
     */
    static String toDotted(String rawPath) {
        String dotted = rawPath.trim().replace('#', '.');
        if (dotted.startsWith("$.")) {
            dotted = dotted.substring(2);
        }
        return INDEX.matcher(dotted).replaceAll("[*]");
    }

    static String withoutWildcards(String dottedPath) {
        return dottedPath.replace("[*]", "");
    }

    static String leaf(String dottedPath) {
        String plain = withoutWildcards(dottedPath);
        return plain.substring(plain.lastIndexOf('.') + 1);
    }

    static String normalize(String name) {
        return WordUtils.normalizeAlphaNumeric(name);
    }

    static boolean isValidPath(String dottedPath) {
        return dottedPath != null && VALID_PATH.matcher(dottedPath).matches();
    }

    static boolean isSimpleYamlKey(String key) {
        return SIMPLE_YAML_KEY.matcher(key).matches();
    }

    /**
     * Returns {@code true} when both paths address the same field, or one is an ancestor of the other.
     */
    static boolean overlaps(String first, String second) {
        String a = withoutWildcards(first);
        String b = withoutWildcards(second);
        return a.equals(b) || a.startsWith(b + ".") || b.startsWith(a + ".");
    }
}
