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
