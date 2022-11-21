package com.endava.cats.generator.format.api;

import java.util.Locale;

public interface PropertySanitizer {

    static String sanitize(String string) {
        return string.replaceAll("[-_#]+", "").toLowerCase(Locale.ROOT);
    }
}
