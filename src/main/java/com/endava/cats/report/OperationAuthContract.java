package com.endava.cats.report;

/**
 * Summarizes the OpenAPI authentication contract of a single operation for passive WFC status-code checks.
 *
 * @param specHasSecuritySchemes {@code true} when the specification declares at least one scheme in
 *                               {@code components.securitySchemes}
 * @param explicitlyPublic       {@code true} when the operation explicitly sets {@code security: []}
 *                               (all operation-level security requirements are empty)
 * @param declares401            {@code true} when the operation declares a {@code 401} or {@code 4XX} response
 */
public record OperationAuthContract(boolean specHasSecuritySchemes, boolean explicitlyPublic, boolean declares401) {
    /**
     * Neutral contract used when no OpenAPI model is available or the operation is undocumented,
     * so no schema-dependent auth fault is emitted.
     */
    public static final OperationAuthContract UNKNOWN = new OperationAuthContract(true, false, true);
}
