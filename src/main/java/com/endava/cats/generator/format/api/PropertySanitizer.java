package com.endava.cats.generator.format.api;

public interface PropertySanitizer {

    static String sanitize(String string) {
        return string.replaceAll("[-_#]+", "");
    }
}
