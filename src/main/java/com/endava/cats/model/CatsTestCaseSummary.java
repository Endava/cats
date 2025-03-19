package com.endava.cats.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Locale;
import java.util.Optional;

/**
 * Represents a summary of a CATS test case.
 * This summary includes information such as test case status, path, and execution time.
 */
@EqualsAndHashCode
@Getter
public class CatsTestCaseSummary implements Comparable<CatsTestCaseSummary> {
    private String scenario;
    private String result;
    private String resultReason;
    private String id;
    private String fuzzer;
    private String path;
    private String resultDetails;
    private String responseBody;
    private double timeToExecuteInSec;
    private long timeToExecuteInMs;
    private String httpMethod;
    private boolean switchedResult;
    private int httpResponseCode;

    /**
     * Creates a CatsTestCaseSummary object from a CatsTestCase.
     *
     * @param testCase The CatsTestCase to generate the summary from.
     * @return A CatsTestCaseSummary representing the summary of the provided CatsTestCase.
     */
    public static CatsTestCaseSummary fromCatsTestCase(CatsTestCase testCase) {
        CatsTestCaseSummary summary = new CatsTestCaseSummary();
        summary.id = testCase.getTestId();
        summary.scenario = testCase.getScenario();
        summary.result = testCase.getResult();
        summary.fuzzer = testCase.getFuzzer();
        summary.path = testCase.getContractPath();
        summary.resultReason = testCase.getResultReason();
        summary.resultDetails = testCase.getResultDetails();
        summary.timeToExecuteInSec = testCase.getResponse().getResponseTimeInMs() / 1000d;
        summary.timeToExecuteInMs = testCase.getResponse().getResponseTimeInMs();
        summary.httpMethod = testCase.getRequest().getHttpMethod().toLowerCase(Locale.ROOT);
        summary.switchedResult = testCase.getResultIgnoreDetails() != null;
        summary.httpResponseCode = testCase.getResponse().getResponseCode();
        String response = Optional.ofNullable(testCase.getResponse().getBody()).orElse("[Empty response body]");
        summary.responseBody = response.substring(0, Math.min(response.length(), 1000));

        return summary;
    }

    @Override
    public int compareTo(CatsTestCaseSummary o) {
        String o1StringPart = this.id.replaceAll("\\d", "");
        String o2StringPart = o.id.replaceAll("\\d", "");

        if (o1StringPart.equalsIgnoreCase(o2StringPart)) {
            return extractInt(this.id) - extractInt(o.id);
        }
        return this.id.compareTo(o.id);
    }

    private int extractInt(String s) {
        String num = s.replaceAll("\\D", "");
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }

    /**
     * Gets a key derived from the test ID with spaces removed.
     *
     * @return The key for the test.
     */
    public String getKey() {
        return id.replace(" ", "");
    }

    /**
     * Checks if the test result is an error.
     *
     * @return True if the result is an error, false otherwise.
     */
    public boolean getError() {
        return this.result.equalsIgnoreCase("error");
    }

    /**
     * Checks if the test result is a warning.
     *
     * @return True if the result is a warning, false otherwise.
     */
    public boolean getWarning() {
        return this.result.equalsIgnoreCase("warning");
    }

    /**
     * Checks of the current response is a 9xx response.
     * 9xx responses are used to signal that the test case was not executed due to technical issues.
     *
     * @return True if the response code is between 900 and 999, false otherwise.
     */
    public boolean is9xxResponse() {
        return this.httpResponseCode >= 900 && this.httpResponseCode < 1000;
    }
}
