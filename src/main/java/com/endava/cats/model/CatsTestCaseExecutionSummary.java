package com.endava.cats.model;

/**
 * Used to hold only details about execution.
 *
 * @param path             the service path
 * @param httpMethod       the http method
 * @param responseTimeInMs the response time in ms
 */
public record CatsTestCaseExecutionSummary(String testId, String path, String httpMethod, long responseTimeInMs) {
}
