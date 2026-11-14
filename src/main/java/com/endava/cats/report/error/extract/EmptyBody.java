package com.endava.cats.report.error.extract;

import org.apache.commons.lang3.StringUtils;

/**
 * The placeholder CATS records for responses without a body, and the test for it.
 */
public final class EmptyBody {
    public static final String PLACEHOLDER = "[Empty response body]";

    private EmptyBody() {
    }

    /**
     * Tells whether a response body carries no information.
     *
     * @param responseBody the response body, may be {@code null}
     * @return {@code true} for a blank body or the {@link #PLACEHOLDER}
     */
    public static boolean isEmpty(String responseBody) {
        return StringUtils.isBlank(responseBody) || PLACEHOLDER.equalsIgnoreCase(responseBody.trim());
    }
}
