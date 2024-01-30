package com.endava.cats.generator.format.api;

import java.util.Locale;

/**
 * An interface defining a property sanitizer.
 * <p>
 * A property sanitizer is responsible for sanitizing a property value by removing any unwanted characters.
 */
public interface PropertySanitizer {

    /**
     * Sanitizes the given string by removing any hyphens (-), underscores (_), and hash symbols (#).
     * The sanitized string is also converted to lowercase using the default locale.
     *
     * @param string The string to sanitize
     * @return The sanitized string
     */
    static String sanitize(String string) {
        return string.replaceAll("[-_#]+", "").toLowerCase(Locale.ROOT);
    }
}
