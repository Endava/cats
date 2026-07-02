package com.endava.cats.auth.wfc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Credentials resolved from WFC Auth and ready to apply to CATS requests.
 *
 * @param headers     request headers
 * @param queryParams request query parameters
 */
public record WfcCredentials(Map<String, String> headers, Map<String, String> queryParams) {
    private static final WfcCredentials EMPTY = new WfcCredentials(Collections.emptyMap(), Collections.emptyMap());

    public WfcCredentials {
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
    }

    /**
     * Empty credentials.
     *
     * @return an empty credential object
     */
    public static WfcCredentials empty() {
        return EMPTY;
    }
}
