package com.endava.cats.model;

/**
 * What the oracle judged when a test failed, which decides how failing tests are grouped into problems: the server
 * message of a crash or a rejection, the violated response rule, the status code itself, or the accepted request.
 */
public enum ProblemKind {
    /**
     * Any 5xx response, grouped by its server message whatever the result reason.
     */
    SERVER_ERROR,
    /**
     * A 4xx response whose verdict is about the rejection, grouped by its server message.
     */
    ERROR_MESSAGE,
    /**
     * The content of the response breaks a rule: the response schema, content type, headers, or an exposure check.
     */
    RESPONSE_VIOLATION,
    /**
     * The status code itself is the finding, e.g. an undocumented response code.
     */
    STATUS_CODE,
    /**
     * A 2xx/3xx response where the fuzzer expected a rejection or another outcome. The body is evidence, never identity.
     */
    ACCEPTED_REQUEST,
    /**
     * No HTTP response: I/O failures, 9xx pseudo statuses and contract linter results.
     */
    NO_RESPONSE;

    /**
     * Returns the kind of a failing or warning test. Precedence: any 5xx response is a {@link #SERVER_ERROR}; then
     * the verdict of the result reason decides; for outcomes (rejected, accepted, crashed) the status class does: a 4xx
     * is an {@link #ERROR_MESSAGE}, a 2xx/3xx an {@link #ACCEPTED_REQUEST}, anything else a {@link #NO_RESPONSE}.
     *
     * @param statusCode   the HTTP status code of the response
     * @param resultReason the result reason as written in reports, matched by prefix
     * @return the problem kind
     */
    public static ProblemKind of(int statusCode, String resultReason) {
        if (statusCode >= 500 && statusCode < 600) {
            return SERVER_ERROR;
        }
        return switch (CatsResultFactory.Reason.fromResultReason(resultReason).map(Verdict::of).orElse(Verdict.OUTCOME)) {
            case RESPONSE_CONTENT -> RESPONSE_VIOLATION;
            case STATUS_CODE -> STATUS_CODE;
            case TRANSPORT -> NO_RESPONSE;
            case OUTCOME -> ofStatusClass(statusCode);
        };
    }

    /**
     * Whether problems of this kind are identified by the server message, i.e. by the mined error template.
     *
     * @return {@code true} for {@link #SERVER_ERROR} and {@link #ERROR_MESSAGE}
     */
    public boolean isIdentifiedByServerMessage() {
        return this == SERVER_ERROR || this == ERROR_MESSAGE;
    }

    private static ProblemKind ofStatusClass(int statusCode) {
        if (statusCode >= 400 && statusCode < 500) {
            return ERROR_MESSAGE;
        }
        return statusCode >= 200 && statusCode < 400 ? ACCEPTED_REQUEST : NO_RESPONSE;
    }

    /**
     * What a result reason judged.
     */
    enum Verdict {
        /**
         * The content of the response against a rule: contract schema, content type, headers, exposure or latency.
         */
        RESPONSE_CONTENT,
        /**
         * The status code itself.
         */
        STATUS_CODE,
        /**
         * No HTTP response was judged: I/O failures, timeouts, throttling or a CATS failure.
         */
        TRANSPORT,
        /**
         * The outcome of the request (rejected, accepted, crashed); the status class decides the kind.
         */
        OUTCOME;

        /**
         * Returns the verdict of a result reason. Exhaustive, so a new reason cannot silently fall into a group.
         *
         * @param reason the result reason
         * @return the verdict
         */
        static Verdict of(CatsResultFactory.Reason reason) {
            return switch (reason) {
                case NOT_MATCHING_RESPONSE_SCHEMA, RESPONSE_CONTENT_TYPE_NOT_MATCHING, MISSING_RESPONSE_HEADERS,
                     MISSING_SECURITY_HEADERS, ERROR_LEAKS_DETECTED, SENSITIVE_DATA_EXPOSED, WRITE_ONLY_FIELDS_EXPOSED,
                     RESPONSE_MATCHES_ARGUMENTS, RESPONSE_TIME_EXCEEDS_MAX, RESPONSE_BODY_TRUNCATED -> RESPONSE_CONTENT;
                case UNDOCUMENTED_RESPONSE_CODE, NOT_IMPLEMENTED, MISSING_ALLOW_HEADER, WRONG_ALLOW_HEADER,
                     OPTIONS_ALLOW_MISMATCH -> STATUS_CODE;
                case CALL_TIMEOUT_EXCEEDED, UNEXPECTED_EXCEPTION, EMPTY_REPLY_FROM_SERVER, CONNECTION_REFUSED, READ_TIMEOUT,
                     WRITE_TIMEOUT, CONNECTION_TIMEOUT, HTTP_PROTOCOL_EXCEPTION, CONNECTION_RESET, RATE_LIMITED -> TRANSPORT;
                case ALL_GOOD, NOT_FOUND, UNEXPECTED_RESPONSE_CODE, UNEXPECTED_BEHAVIOUR, MASS_ASSIGNMENT_DETECTED,
                     UNDECLARED_FIELD_ACCEPTED, SERVER_ERROR, SSRF_REFLECTED, INTERNAL_TARGET_REFLECTED_SSRF,
                     CLOUD_METADATA_REFLECTED_SSRF, FILE_CONTENT_EXPOSED_SSRF, NETWORK_ERROR_SSRF, DNS_RESOLUTION_SSRF,
                     HTTP_CLIENT_SSRF, COMMAND_INJECTION, POSSIBLE_COMMAND_INJECTION, NOSQL_INJECTION, SQL_INJECTION,
                     LDAP_INJECTION, POSSIBLE_LDAP_INJECTION, LDAP_DATA_EXTRACTION, LDAP_AUTHENTICATION_BYPASS,
                     XSS_INJECTION, POTENTIAL_IDOR, READ_BROKEN_AFTER_FUZZING, PUT_RETRY_SERVER_ERROR, PUT_RETRY_REJECTED,
                     PUT_RETRY_BROKE_READ, PUT_NOT_IDEMPOTENT, UPDATE_READ_INCONSISTENCY, STALE_IF_MATCH_ACCEPTED,
                     STALE_IF_MATCH_SERVER_ERROR, CONDITIONAL_GET_IGNORED, CONDITIONAL_GET_SERVER_ERROR,
                     CREATED_RESOURCE_UNAVAILABLE, CROSS_PARENT_SERVER_ERROR, CROSS_PARENT_RESOURCE_EXPOSURE,
                     PATH_TRAVERSAL, HTTP_METHOD_OVERRIDE_ACCEPTED, ARITHMETIC_TOTAL_MISMATCH_ACCEPTED,
                     PATH_BODY_ID_MISMATCH_ACCEPTED, PATCH_CLOBBERED_OMITTED_FIELDS, PAGINATION_LIMIT_IGNORED,
                     REPEATED_CREATE_PUT, MISLEADING_CREATE_PUT, PARTIAL_UPDATE_PUT, REJECTED_UPDATE_PARTIALLY_APPLIED,
                     FILTER_PARAMETER_IGNORED -> OUTCOME;
            };
        }
    }
}
