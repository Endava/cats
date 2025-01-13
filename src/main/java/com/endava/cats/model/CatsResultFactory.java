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
        String message = "Response does NOT match expected result. Response code [%s] is documented, but response body does NOT match the corresponding schema.".formatted(receivedResponseCode);
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
        return new CatsResult("Response HTTP code 501: you forgot to implement this functionality!", Reason.NOT_IMPLEMENTED.description());
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
        String message = "Unexpected behaviour: expected %s, actual [%s]".formatted(expectedResponseCode, receivedResponseCode);
        String reason = Reason.UNEXPECTED_BEHAVIOUR.value() + " %s".formatted(receivedResponseCode);

        return new CatsResult(message, reason);
    }

    /**
     * Creates an unexpected response code message and reason. Usually caused by a mismatch between what is documented and what the service is responding.
     *
     * @param receivedResponseCode the http response code received from the service
     * @param expectedResponseCode the expected http response code
     * @return a CatsResult to use in reporting
     */
    static CatsResult createUnexpectedResponseCode(String receivedResponseCode, String expectedResponseCode) {
        String message = "Response does NOT match expected result. Response code is NOT from a list of expected codes for this FUZZER: expected %s, actual [%s]".formatted(expectedResponseCode, receivedResponseCode);
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
        POTENTIAL_IDOR("Potential IDOR vulnerability detected", "The API may be vulnerable to Insecure Direct Object Reference, allowing unauthorized access to resources");


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
