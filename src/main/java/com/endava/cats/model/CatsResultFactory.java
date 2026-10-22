package com.endava.cats.model;

import java.util.List;

/**
 * Creates expected cats results with description and reason.
 */
public interface CatsResultFactory {

    /**
     * Creates a message and reason for the case when the received response is documented and response body matches response schema.
     *
     * @param receivedResponseCode the HTTP response code received from the service
     * @return a CatsResult to use in reports
     */
    static CatsResult createExpectedResponse(String receivedResponseCode) {
        String message = "Response matches expected result. Response code [%s] is documented and response body matches the corresponding schema.".formatted(receivedResponseCode);
        String reason = Reason.ALL_GOOD.value();

        return new CatsResult(message, reason);
    }

    /**
     * Creates a message and reason for the case when the received response is documented, BUT response body doesn't match response schema.
     *
     * @param receivedResponseCode the HTTP response code received from the service
     * @return a CatsResult to use in reports
     */
    static CatsResult createNotMatchingResponseSchema(String receivedResponseCode) {
        return createNotMatchingResponseSchema(receivedResponseCode, "");
    }

    static CatsResult createNotMatchingResponseSchema(String receivedResponseCode, String validationDetails) {
        String message = "Response does NOT match expected result. Response code [%s] is documented, but response body does NOT match the corresponding schema.".formatted(receivedResponseCode);
        if (validationDetails != null && !validationDetails.isBlank()) {
            message += " Validation errors: " + validationDetails;
        }
        String reason = Reason.NOT_MATCHING_RESPONSE_SCHEMA.value();

        return new CatsResult(message, reason);
    }

    /**
     * Creates a CatsResult indicating that the response content type does not match the contract.
     *
     * @param expected The list of expected content types.
     * @param actual   The actual content type received in the response.
     * @return A CatsResult indicating the mismatch in content types.
     */
    static CatsResult createNotMatchingContentType(List<String> expected, String actual) {
        String message = "Response content type not matching the contract: expected %s, actual [%s]".formatted(expected, actual);
        String reason = Reason.RESPONSE_CONTENT_TYPE_NOT_MATCHING.value();
        return new CatsResult(message, reason);
    }

    /**
     * Creates a message and reason for the case when the received response code is 501.
     *
     * @return a CatsResult to use in reports
     */
    static CatsResult createNotImplemented() {
        return new CatsResult("Response HTTP code 501: you forgot to implement this functionality!", Reason.NOT_IMPLEMENTED.value());
    }

    /**
     * Creates a message and reason for the case when the received response code is 404.
     *
     * @return a CatsResult to use in reports
     */
    static CatsResult createNotFound() {
        return new CatsResult("Response HTTP code 404: you might need to provide business context using --refData or --urlParams", Reason.NOT_FOUND.value());
    }

    /**
     * Creates a message and reason when response time exceeds maximum.
     *
     * @param receivedResponseTime the received response time in ms
     * @param maxResponseTime      the max response time
     * @return a CatsResult to use in reporting
     */
    static CatsResult createResponseTimeExceedsMax(long receivedResponseTime, long maxResponseTime) {
        String message = "Test case executed successfully, but response time exceeds --maxResponseTimeInMs: actual %d, max %d".formatted(receivedResponseTime, maxResponseTime);
        String reason = Reason.RESPONSE_TIME_EXCEEDS_MAX.value();

        return new CatsResult(message, reason);
    }

    static CatsResult createResponseBodyTruncated(long capturedBodyBytes, long maxResponseBytes, long declaredContentLength) {
        String declaredLength = declaredContentLength < 0 ? "unknown" : String.valueOf(declaredContentLength);
        String message = "Response body exceeded --maxResponseBytes and was truncated: captured %d bytes, max %d, declared Content-Length %s"
                .formatted(capturedBodyBytes, maxResponseBytes, declaredLength);
        return new CatsResult(message, Reason.RESPONSE_BODY_TRUNCATED.value());
    }

    static CatsResult createCallTimeoutExceeded(long responseTimeInMs) {
        return new CatsResult("HTTP call exceeded --callTimeout after %d ms".formatted(responseTimeInMs), Reason.CALL_TIMEOUT_EXCEEDED.value());
    }

    static CatsResult createExceptionalResponse(CatsResponse.ExceptionalResponse exceptional, long responseTimeInMs) {
        return switch (exceptional) {
            case EMPTY_BODY -> new CatsResult(
                    "Network/I/O error (code 952): server closed the connection without sending a response body",
                    Reason.EMPTY_REPLY_FROM_SERVER.value());
            case CONNECTION_REFUSED -> new CatsResult(
                    "Network/I/O error (code 953): connection refused — verify that the target service is running and reachable at --server",
                    Reason.CONNECTION_REFUSED.value());
            case READ_TIMEOUT -> new CatsResult(
                    "Network/I/O error (code 954): read timeout after %d ms — consider increasing --readTimeout".formatted(responseTimeInMs),
                    Reason.READ_TIMEOUT.value());
            case WRITE_TIMEOUT -> new CatsResult(
                    "Network/I/O error (code 955): write timeout after %d ms — consider increasing --writeTimeout".formatted(responseTimeInMs),
                    Reason.WRITE_TIMEOUT.value());
            case CONNECTION_TIMEOUT -> new CatsResult(
                    "Network/I/O error (code 956): connection timeout after %d ms — consider increasing --connectionTimeout".formatted(responseTimeInMs),
                    Reason.CONNECTION_TIMEOUT.value());
            case PROTOCOL_EXCEPTION -> new CatsResult(
                    "Network/I/O error (code 957): HTTP protocol violation while reading response from server",
                    Reason.HTTP_PROTOCOL_EXCEPTION.value());
            case CONNECTION_RESET -> new CatsResult(
                    "Network/I/O error (code 958): connection reset by peer while processing the request",
                    Reason.CONNECTION_RESET.value());
            case CALL_TIMEOUT, NO_BODY -> createCallTimeoutExceeded(responseTimeInMs);
        };
    }

    /**
     * Creates am unexpected exception message and reason. Typically, as a last resort when cannot determine other reasons.
     *
     * @param fuzzer       the fuzzer name causing the exception
     * @param errorMessage the message of the exception
     * @return a CatsResult to use in reporting
     */
    static CatsResult createUnexpectedException(String fuzzer, String errorMessage) {
        String message = "Fuzzer [%s] failed due to [%s]".formatted(fuzzer, errorMessage);
        String reason = Reason.UNEXPECTED_EXCEPTION.value();

        return new CatsResult(message, reason);
    }

    /**
     * Creates an error leaks detected message and reason. This happens when the response contains error messages that are not expected.
     *
     * @param keywords the keywords detected in the response
     * @return a CatsResult to use in reporting
     */
    static CatsResult createErrorLeaksDetectedInResponse(List<String> keywords) {
        String message = "The following keywords were detected in the response which might suggest an error details leak: %s".formatted(keywords);
        String reason = Reason.ERROR_LEAKS_DETECTED.value();

        return new CatsResult(message, reason);
    }

    /**
     * Creates an unexpected behaviour message and reason. This is not caused by an abnormal functioning of the application,
     * but rather a response code that was not expected, nor documented, nor known not to typically be documented.
     *
     * @param receivedResponseCode the http response code received from the service
     * @param expectedResponseCode the expected http response code
     * @return a CatsResult to use in reporting
     */
    static CatsResult createUnexpectedBehaviour(String receivedResponseCode, String expectedResponseCode) {
        String base = "Unexpected behaviour: expected %s, actual [%s] (response code is neither expected for this fuzzer nor documented in the OpenAPI contract)"
                .formatted(expectedResponseCode, receivedResponseCode);
        if (("401".equals(receivedResponseCode) || "403".equals(receivedResponseCode))
                && !expectedResponseCode.contains(receivedResponseCode)) {
            base += " — authentication/authorization failed; verify credentials supplied via --headers, --user, or --wfcAuth";
        }
        String reason = Reason.UNEXPECTED_BEHAVIOUR.value() + " %s".formatted(receivedResponseCode);

        return new CatsResult(base, reason);
    }

    /**
     * Creates an unexpected response code message and reason. Usually caused by a mismatch between what is documented and what the service is responding.
     *
     * @param receivedResponseCode the http response code received from the service
     * @param expectedResponseCode the expected http response code
     * @return a CatsResult to use in reporting
     */
    static CatsResult createUnexpectedResponseCode(String receivedResponseCode, String expectedResponseCode) {
        String message = "Response does NOT match expected result. Response code is NOT from a list of expected codes for this FUZZER: expected %s, actual [%s] (response code is documented in the contract, but unexpected for this scenario)"
                .formatted(expectedResponseCode, receivedResponseCode);
        if (("401".equals(receivedResponseCode) || "403".equals(receivedResponseCode))
                && !expectedResponseCode.contains(receivedResponseCode)) {
            message += " — authentication/authorization failed; verify credentials supplied via --headers, --user, or --wfcAuth";
        }
        String reason = Reason.UNEXPECTED_RESPONSE_CODE.value() + ": %s".formatted(receivedResponseCode);

        return new CatsResult(message, reason);
    }

    /**
     * Creates an undocumented response code message and reason. This happens when the returned response code is not documented inside the contract.
     *
     * @param receivedResponseCode    the received response code
     * @param expectedResponseCode    the expected response code
     * @param documentedResponseCodes all documented response codes
     * @return a CatsResult to use in reporting
     */
    static CatsResult createUndocumentedResponseCode(String receivedResponseCode, String expectedResponseCode, String documentedResponseCodes) {
        String message = "Response does NOT match expected result. Response code is from a list of expected codes for this FUZZER, but it is undocumented: expected %s, actual [%s], documented response codes: %s".formatted(expectedResponseCode, receivedResponseCode, documentedResponseCodes);
        String reason = Reason.UNDOCUMENTED_RESPONSE_CODE.value() + ": %s".formatted(receivedResponseCode);

        return new CatsResult(message, reason);
    }


    /**
     * Holds message and reason information when exceptional situations happen when running CATS.
     *
     * @param message the message result
     * @param reason  a short description of the message that will be displayed in summary page
     */
    record CatsResult(String message, String reason) {
    }

    enum Reason {
        ALL_GOOD("All Good!", "The response matches the expected result"),
        NOT_MATCHING_RESPONSE_SCHEMA("Not matching response schema", "The response body does NOT match the corresponding schema defined in the OpenAPI contract"),
        NOT_IMPLEMENTED("Not implemented", "You forgot to implement this functionality!"),
        NOT_FOUND("Not found", "You might need to provide business context using --refData or --urlParams"),
        RESPONSE_TIME_EXCEEDS_MAX("Response time exceeds max", "The response time exceeds the maximum configured response time supplied using --maxResponseTimeInMs, default is 0 i.e no limit"),
        RESPONSE_BODY_TRUNCATED("Response body truncated", "The response body exceeded --maxResponseBytes and was truncated before body-dependent checks could complete"),
        CALL_TIMEOUT_EXCEEDED("Call timeout exceeded", "The HTTP call exceeded the configured --callTimeout fuzzing budget"),
        UNEXPECTED_EXCEPTION("Unexpected exception", "An unexpected exception occurred. This might suggest an issue with CATS itself"),
        ERROR_LEAKS_DETECTED("Error details leak", "The response contains error messages that might expose sensitive information"),
        UNEXPECTED_RESPONSE_CODE("Unexpected response code", "The response code is documented inside the contract, but not expected for the current fuzzer"),
        UNDOCUMENTED_RESPONSE_CODE("Undocumented response code", "The response code is expected for the current fuzzer, but not documented inside the contract"),
        RESPONSE_CONTENT_TYPE_NOT_MATCHING("Response content type not matching the contract", "The response content type does not match the one defined in the OpenAPI contract"),
        UNEXPECTED_BEHAVIOUR("Unexpected behaviour", "CATS run the test case successfully, but the response code was not expected, nor documented, nor known to typically be documented"),
        RESPONSE_MATCHES_ARGUMENTS("Response matches arguments", "The response matches the expected arguments supplied to the fuzzer"),
        MASS_ASSIGNMENT_DETECTED("Mass Assignment vulnerability detected", "The API accepts additional fields that were not defined in the schema, which could lead to mass assignment vulnerabilities"),
        UNDECLARED_FIELD_ACCEPTED("Undeclared field accepted", "The API accepted a field that is not declared in the OpenAPI contract schema"),
        SERVER_ERROR("Server error", "The server returned a 5xx error code indicating an internal server error"),
        SSRF_REFLECTED("SSRF payload reflected in response", "The SSRF payload was reflected in the response, indicating a potential Server-Side Request Forgery vulnerability"),
        INTERNAL_TARGET_REFLECTED_SSRF("Internal target reflected in response", "The response indicates that the server attempted to access an internal target, suggesting a potential SSRF vulnerability"),
        CLOUD_METADATA_REFLECTED_SSRF("Cloud metadata service accessed", "The server accessed cloud metadata services, indicating a critical SSRF vulnerability that could expose sensitive cloud credentials"),
        FILE_CONTENT_EXPOSED_SSRF("File content exposed via SSRF", "File content was exposed through SSRF, indicating the server can be used to read local files"),
        NETWORK_ERROR_SSRF("Network error reveals SSRF attempt", "Network error messages in the response reveal that the server attempted an SSRF request"),
        DNS_RESOLUTION_SSRF("DNS resolution error reveals SSRF attempt", "DNS resolution errors in the response indicate that the server attempted to resolve a domain, revealing an SSRF vulnerability"),
        HTTP_CLIENT_SSRF("HTTP client error reveals SSRF attempt", "HTTP client error messages in the response reveal that the server made an outbound HTTP request, indicating an SSRF vulnerability"),
        COMMAND_INJECTION("Command injection vulnerability detected", "The API is vulnerable to command injection, allowing execution of arbitrary system commands"),
        POSSIBLE_COMMAND_INJECTION("Possible command injection vulnerability", "The response suggests a possible command injection vulnerability that requires further investigation"),
        NOSQL_INJECTION("NoSQL injection vulnerability detected", "The API is vulnerable to NoSQL injection, allowing manipulation of database queries"),
        SQL_INJECTION("SQL injection vulnerability detected", "The API is vulnerable to SQL injection, allowing manipulation of database queries"),
        XSS_INJECTION("XSS payload reflected in response", "The XSS payload was reflected in the response without proper sanitization, indicating a Cross-Site Scripting vulnerability"),
        MISSING_SECURITY_HEADERS("Missing recommended security headers", "The response is missing recommended security headers that help protect against common web vulnerabilities"),
        MISSING_RESPONSE_HEADERS("Missing response headers", "The response is missing headers that are documented in the OpenAPI contract"),
        POTENTIAL_IDOR("Heuristic IDOR signal detected", "A modified identifier was returned in a successful response, but resource ownership was not verified"),
        WRITE_ONLY_FIELDS_EXPOSED("WriteOnly fields exposed in response", "The response contains fields marked as writeOnly in the OpenAPI contract, which must never be returned by the service"),
        SENSITIVE_DATA_EXPOSED("Sensitive data exposed in response", "The response contains undeclared fields with sensitive names (like password, secret or apiKey) and unmasked values"),
        READ_BROKEN_AFTER_FUZZING("Read broken after fuzzing", "An operation that returned 2xx for unmodified requests earlier in the run returns a server error after the path was fuzzed, suggesting that fuzzed writes stored data which breaks reads"),
        EMPTY_REPLY_FROM_SERVER("Empty reply from server", "The server closed the connection without sending an HTTP response body"),
        CONNECTION_REFUSED("Connection refused", "The target service refused the TCP connection; verify that the service is running and reachable at --server"),
        READ_TIMEOUT("Read timeout", "Timed out waiting to read the HTTP response from the server; consider increasing --readTimeout"),
        WRITE_TIMEOUT("Write timeout", "Timed out writing the HTTP request to the server; consider increasing --writeTimeout"),
        CONNECTION_TIMEOUT("Connection timeout", "Timed out establishing a TCP connection to the server; consider increasing --connectionTimeout"),
        HTTP_PROTOCOL_EXCEPTION("HTTP protocol exception", "The server returned a malformed HTTP response that violated the HTTP protocol"),
        CONNECTION_RESET("Connection reset", "The TCP connection was reset by the peer while processing the request"),
        PUT_RETRY_SERVER_ERROR("PUT retry caused server error", "Repeating an identical previously successful PUT request caused a 5xx server error"),
        PUT_RETRY_REJECTED("PUT retry rejected", "Repeating an identical previously successful PUT request was rejected by the server"),
        PUT_RETRY_BROKE_READ("PUT retry broke resource read", "A GET request after repeating an identical PUT returned a 5xx server error"),
        PUT_NOT_IDEMPOTENT("PUT is not idempotent", "Resource state changed after repeating an identical PUT request"),
        UPDATE_READ_INCONSISTENCY("Possible update/read inconsistency", "Comparable writable fields sent in a successful PUT or PATCH differed when subsequently read back via GET"),
        STALE_IF_MATCH_ACCEPTED("Stale If-Match accepted", "An update carrying a stale If-Match ETag succeeded instead of being rejected with 412 Precondition Failed"),
        STALE_IF_MATCH_SERVER_ERROR("Stale If-Match caused server error", "An update carrying a stale If-Match ETag caused a 5xx server error"),
        CONDITIONAL_GET_IGNORED("Conditional GET ignored", "A GET carrying If-None-Match with the current ETag returned 200 OK instead of 304 Not Modified"),
        CONDITIONAL_GET_SERVER_ERROR("Conditional GET caused server error", "A GET carrying If-None-Match caused a 5xx server error"),
        CREATED_RESOURCE_UNAVAILABLE("Created resource unavailable", "A POST returned 201 Created with a Location header, but a subsequent GET on that Location returned 404 or 410"),
        CROSS_PARENT_SERVER_ERROR("Cross-parent request caused server error", "Requesting a child resource under a different parent resource identifier caused a 5xx server error"),
        CROSS_PARENT_RESOURCE_EXPOSURE("Potential cross-parent resource exposure", "A child resource was returned successfully under a different parent resource identifier, indicating a potential BOLA/IDOR issue"),
        MISSING_ALLOW_HEADER("Missing Allowed header", "The server returned 405 Method Not Allowed without the mandatory Allow response header"),
        WRONG_ALLOW_HEADER("Wrong Allowed header", "The server returned 405 Method Not Allowed, but the Allow response header listed the rejected HTTP method as allowed");


        private final String value;
        private final String description;

        Reason(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String description() {
            return description;
        }

        public String value() {
            return value;
        }
    }
}
