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
        String reason = Reason.ALL_GOOD.description();

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
        String reason = Reason.NOT_MATCHING_RESPONSE_SCHEMA.description();

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
        String reason = Reason.RESPONSE_CONTENT_TYPE_NOT_MATCHING.description();
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
        return new CatsResult("Response HTTP code 404: you might need to provide business context using --refData or --urlParams", Reason.NOT_FOUND.description());
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
        String reason = Reason.RESPONSE_TIME_EXCEEDS_MAX.description();

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
        String reason = Reason.UNEXPECTED_EXCEPTION.description();

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
        String reason = Reason.ERROR_LEAKS_DETECTED.description();

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
        String reason = Reason.UNEXPECTED_BEHAVIOUR.description() + " %s".formatted(receivedResponseCode);

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
        String reason = Reason.UNEXPECTED_RESPONSE_CODE.description() + ": %s".formatted(receivedResponseCode);

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
        String reason = Reason.UNDOCUMENTED_RESPONSE_CODE.description() + ": %s".formatted(receivedResponseCode);

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
        UNEXPECTED_BEHAVIOUR("Unexpected behaviour", "CATS run the test case successfully, but the response code was not expected, nor documented, nor known to typically be documented");

        private final String reason;
        private final String description;

        Reason(String reason, String description) {
            this.reason = reason;
            this.description = description;
        }

        public String description() {
            return description;
        }

        public String reason() {
            return reason;
        }
    }
}
