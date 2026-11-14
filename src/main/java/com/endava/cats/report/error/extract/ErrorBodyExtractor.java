package com.endava.cats.report.error.extract;

import java.util.Optional;

/**
 * Extracts the root cause from one family of error body formats (HTML pages, JSON objects, plain text).
 */
public interface ErrorBodyExtractor {

    /**
     * Extracts the root cause of an error body.
     *
     * @param body the non-blank response body
     * @return the root cause, or empty when the body is not in a format this extractor handles
     */
    Optional<ErrorCause> extract(String body);
}
