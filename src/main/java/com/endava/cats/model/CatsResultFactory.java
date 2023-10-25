package com.endava.cats.model;

/**
 * Creates expected cats results with description and reason.
 */
public abstract class CatsResultFactory {

    /**
     * Creates a message and reason for the case when received response is documented and response body matches response schema.
     *
     * @param receivedResponseCode the HTTP response code received from the service
     * @return a CatsResult to use in reports
     */
    public static CatsResult createExpectedResponse(String receivedResponseCode) {
        String message = "Response matches expected result. Response code [%s] is documented and response body matches the corresponding schema.".formatted(receivedResponseCode);
        String reason = "All Good!";

        return new CatsResult(message, reason);
    }

    /**
     * Creates a message and reason for the case when received response is documented, BUT response body doesn't matches response schema.
     *
     * @param receivedResponseCode the HTTP response code received from the service
     * @return a CatsResult to use in reports
     */
    public static CatsResult createNotMatchingResponseSchema(String receivedResponseCode) {
        String message = "Response does NOT match expected result. Response code [%s] is documented, but response body does NOT matches the corresponding schema.".formatted(receivedResponseCode);
        String reason = "Not Matching Response Schema";

        return new CatsResult(message, reason);
    }

    public static CatsResult createNotImplemented() {
        return new CatsResult("Response HTTP code 501: you forgot to implement this functionality!", "Not Implemented");
    }

    public static CatsResult createNotFound() {
        return new CatsResult("Response HTTP code 404: you might need to provide business context using --refData or --urlParams", "Not Found");
    }

    public static CatsResult createResponseTimeExceedsMax(long receivedResponseTime, long maxResponseTime) {
        String message = "Test case executed successfully, but response time exceeds --maxResponseTimeInMs: actual %d, max %d".formatted(receivedResponseTime, maxResponseTime);
        String reason = "Response time exceeds max";

        return new CatsResult(message, reason);
    }

    public static CatsResult createUnexpectedException(String fuzzer, String errorMessage) {
        String message = "Fuzzer [%s] failed due to [%s]".formatted(fuzzer, errorMessage);
        String reason = "Unexpected Exception";

        return new CatsResult(message, reason);
    }

    public static CatsResult createUnexpectedBehaviour(String receivedResponseCode, String expectedResponseCode) {
        String message = "Unexpected behaviour: expected %s, actual [%s]".formatted(expectedResponseCode, receivedResponseCode);
        String reason = "Unexpected behaviour: %s".formatted(receivedResponseCode);

        return new CatsResult(message, reason);
    }

    public static CatsResult createUnexpectedResponseCode(String receivedResponseCode, String expectedResponseCode) {
        String message = "Response does NOT match expected result. Response code is NOT from a list of expected codes for this FUZZER: expected %s, actual [%s]".formatted(expectedResponseCode, receivedResponseCode);
        String reason = "Unexpected Response Code: %s".formatted(receivedResponseCode);

        return new CatsResult(message, reason);
    }

    public static CatsResult createUndocumentedResponseCode(String receivedResponseCode, String expectedResponseCode, String documentedResponseCodes) {
        String message = "Response does NOT match expected result. Response code is from a list of expected codes for this FUZZER, but it is undocumented: expected %s, actual [%s], documented response codes: %s".formatted(expectedResponseCode, receivedResponseCode, documentedResponseCodes);
        String reason = "Undocumented Response Code: %s".formatted(receivedResponseCode);

        return new CatsResult(message, reason);
    }


    public record CatsResult(String message, String reason) {
    }
}
