package com.endava.cats.report;

import com.webfuzzing.commons.faults.FaultCategory;

import java.util.Objects;

/**
 * CATS-specific fault categories in the WFC-reserved {@code 900..999} custom code range, implementing
 * {@link FaultCategory} for faults not yet covered by WFC's built-in {@code 1xx/2xx/3xx} categories.
 */
public enum CatsWfcFaultCategory implements FaultCategory {
    CATS_COMMAND_INJECTION(
            900,
            "Command Injection (OS Command Execution)",
            "vulnerableToCommandInjection",
            "Input data was not properly sanitized and triggered OS command execution indicators in the response."),
    CATS_NOSQL_INJECTION(
            901,
            "NoSQL Injection",
            "vulnerableToNoSqlInjection",
            "Input data was not properly sanitized and triggered NoSQL query manipulation indicators in the response."),
    CATS_SENSITIVE_DATA_EXPOSURE(
            902,
            "Sensitive or WriteOnly Data Exposure",
            "exposesSensitiveOrWriteOnlyData",
            "The API response exposed fields marked as writeOnly in the OpenAPI contract or unmasked sensitive credentials."),
    CATS_MISSING_SECURITY_HEADERS(
            903,
            "Missing Security or Documented Response Headers",
            "missingSecurityOrDocumentedHeaders",
            "The HTTP response omitted mandatory security headers or response headers declared in the OpenAPI contract."),
    CATS_READ_BROKEN_AFTER_FUZZING(
            904,
            "Read Broken After Fuzzing",
            "readBrokenAfterFuzzing",
            "A GET operation that succeeded before fuzzing returned a 5xx server error after write operations on the path were fuzzed."),
    CATS_UPDATE_READ_INCONSISTENCY(
            905,
            "Update/Read State Inconsistency",
            "updateReadInconsistency",
            "Comparable writable fields sent in a successful PUT or PATCH differed when the resource was subsequently read back."),
    CATS_STALE_ETAG_ACCEPTED(
            906,
            "Stale ETag Precondition Accepted",
            "staleEtagAccepted",
            "An update carrying a stale If-Match ETag succeeded instead of being rejected with 412 Precondition Failed."),
    CATS_UNEXPECTED_BEHAVIOUR(
            907,
            "Unexpected API Behaviour",
            "unexpectedApiBehaviour",
            "The API returned an unexpected or undocumented response that did not match the fuzzer's oracle expectations.");

    private final int code;
    private final String descriptiveName;
    private final String testCaseLabel;
    private final String fullDescription;

    CatsWfcFaultCategory(int code, String descriptiveName, String testCaseLabel, String fullDescription) {
        this.code = code;
        this.descriptiveName = Objects.requireNonNull(descriptiveName);
        this.testCaseLabel = Objects.requireNonNull(testCaseLabel);
        this.fullDescription = Objects.requireNonNull(fullDescription);
    }

    @Override
    public String getId() {
        return name();
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDescriptiveName() {
        return descriptiveName;
    }

    @Override
    public String getTestCaseLabel() {
        return testCaseLabel;
    }

    @Override
    public String getFullDescription() {
        return fullDescription;
    }
}
