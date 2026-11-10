package com.endava.cats.report;

import com.webfuzzing.commons.faults.FaultCategory;

import java.util.Objects;

/**
 * CATS-specific fault categories in the WFC-reserved {@code 900..999} custom code range, implementing
 * {@link FaultCategory} for faults not yet covered by WFC's built-in {@code 1xx/2xx/3xx} categories.
 */
public enum CatsWfcFaultCategory implements FaultCategory {
    CATS_UNEXPECTED_BEHAVIOUR(
            907,
            "Unexpected API Behaviour",
            "unexpectedApiBehaviour",
            "The API returned an unexpected or undocumented response that did not match the fuzzer's oracle expectations."),
    CATS_FILTER_PARAMETER_IGNORED(
            908,
            "Collection Filter Parameter Ignored",
            "filterParameterIgnored",
            "A GET collection request filtered by an exact-match query parameter returned items whose matching field holds a different value, so the filtered result is not a subset matching the filter."),
    CATS_PAGINATION_LIMIT_IGNORED(
            909,
            "Collection Pagination Limit Ignored",
            "paginationLimitIgnored",
            "A GET collection request with a pagination limit parameter set to 1 (e.g., limit, size, pageSize, per_page) returned more than one item, so the requested page size was not honored."),
    CATS_CONDITIONAL_GET_IGNORED(
            910,
            "Conditional GET Ignored",
            "conditionalGetIgnored",
            "A GET request carrying If-None-Match with the current ETag of the resource returned 200 with the full representation instead of 304 Not Modified (RFC 9110 section 13.1.2)."),
    CATS_ARITHMETIC_TOTAL_MISMATCH_ACCEPTED(
            911,
            "Inconsistent Arithmetic Total Accepted",
            "arithmeticTotalMismatchAccepted",
            "A request whose client-supplied total contradicts its component fields (e.g., total != quantity * unitPrice) was accepted and the inconsistent total was reflected back, instead of being rejected or recomputed server-side."),
    CATS_PATH_BODY_ID_MISMATCH_ACCEPTED(
            912,
            "Path And Body Identifier Mismatch Accepted",
            "pathBodyIdMismatchAccepted",
            "A PUT or PATCH request whose body identifier contradicted the identifier in the URL path was accepted and the body identifier was reflected back, so the target resource of the write is ambiguous.");

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
