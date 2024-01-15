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
        String reason = "All Good!";

        return new CatsResult(message, reason);
    }

    /**
     * Creates a message and reason for the case when the received response is documented, BUT response body doesn't match response schema.
     *
     * @param receivedResponseCode the HTTP response code received from the service
     * @return a CatsResult to use in reports
     */
    static CatsResult createNotMatchingResponseSchema(String receivedResponseCode) {
        String message = "Response does NOT match expected result. Response code [%s] is documented, but response body does NOT matches the corresponding schema.".formatted(receivedResponseCode);
        String reason = "Not Matching Response Schema";

        return new CatsResult(message, reason);
    }

    static CatsResult createNotMatchingContentType(List<String> expected, String actual) {
        String message = "Response content type not matching the contract: expected %s, actual [%s]".formatted(expected, actual);
        String reason = "Response content type not matching the contract";
        return new CatsResult(message, reason);
    }

    /**
     * Creates a message and reason for the case when the received response code is 501.
     *
     * @return a CatsResult to use in reports
     */
    static CatsResult createNotImplemented() {
        return new CatsResult("Response HTTP code 501: you forgot to implement this functionality!", "Not Implemented");
    }

    /**
     * Creates a message and reason for the case when the received response code is 404.
     *
     * @return a CatsResult to use in reports
     */
    static CatsResult createNotFound() {
        return new CatsResult("Response HTTP code 404: you might need to provide business context using --refData or --urlParams", "Not Found");
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
        String reason = "Response time exceeds max";

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
        String reason = "Unexpected Exception";

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
        String reason = "Unexpected behaviour: %s".formatted(receivedResponseCode);

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
        String reason = "Unexpected Response Code: %s".formatted(receivedResponseCode);

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
        String reason = "Undocumented Response Code: %s".formatted(receivedResponseCode);

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
}
