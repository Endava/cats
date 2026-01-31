package com.endava.cats.model;

import lombok.Builder;

/**
 * Immutable value object that encapsulates all data captured during a test case execution.
 * This object is returned by {@link com.endava.cats.io.ServiceCaller} and contains all the
 * information needed to record the test case results without requiring scattered mutations
 * of {@link com.endava.cats.report.TestCaseListener}.
 * <p>
 * This class serves as a data transfer object between the service caller and the test case
 * listener, promoting a cleaner separation of concerns and making the test execution flow
 * more explicit and easier to understand.
 *
 * @param request         the HTTP request that was sent to the service
 * @param response        the HTTP response received from the service
 * @param fullRequestPath the complete URL path including server, path, and query parameters
 * @param contractPath    the OpenAPI contract path (e.g., /pets/{petId})
 * @param server          the server URL where the request was sent
 * @param validJson       whether the request payload was valid JSON
 * @param durationMs      the total duration of the HTTP call in milliseconds
 */
@Builder
public record TestCaseResult(
        CatsRequest request,
        CatsResponse response,
        String fullRequestPath,
        String contractPath,
        String server,
        boolean validJson,
        long durationMs
) {
    /**
     * Creates a TestCaseResult from an exceptional case where the HTTP call failed.
     * This is used when an IOException or IllegalStateException occurs during the service call.
     *
     * @param request      the HTTP request that was attempted
     * @param response     the exceptional response containing error details
     * @param contractPath the OpenAPI contract path
     * @param server       the server URL
     * @param validJson    whether the request payload was valid JSON
     * @param durationMs   the duration until the exception occurred
     * @return a TestCaseResult representing the exceptional case
     */
    public static TestCaseResult fromException(
            CatsRequest request,
            CatsResponse response,
            String contractPath,
            String server,
            boolean validJson,
            long durationMs) {
        return TestCaseResult.builder()
                .request(request)
                .response(response)
                .fullRequestPath(request.getUrl())
                .contractPath(contractPath)
                .server(server)
                .validJson(validJson)
                .durationMs(durationMs)
                .build();
    }

    /**
     * Creates a TestCaseResult from a successful HTTP call.
     *
     * @param request      the HTTP request that was sent
     * @param response     the HTTP response received
     * @param contractPath the OpenAPI contract path
     * @param server       the server URL
     * @param validJson    whether the request payload was valid JSON
     * @param durationMs   the duration of the HTTP call
     * @return a TestCaseResult representing the successful call
     */
    public static TestCaseResult fromSuccess(
            CatsRequest request,
            CatsResponse response,
            String contractPath,
            String server,
            boolean validJson,
            long durationMs) {
        return TestCaseResult.builder()
                .request(request)
                .response(response)
                .fullRequestPath(request.getUrl())
                .contractPath(contractPath)
                .server(server)
                .validJson(validJson)
                .durationMs(durationMs)
                .build();
    }
}
