package com.endava.cats.model.generator.api;

public interface PropertySanitizer {

    static String sanitize(String string) {
        return string.replaceAll("[-_#]+", "");
    }
}
