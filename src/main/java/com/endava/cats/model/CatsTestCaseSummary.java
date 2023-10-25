package com.endava.cats.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Locale;

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
    private double timeToExecuteInSec;
    private String httpMethod;

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
        summary.httpMethod = testCase.getRequest().getHttpMethod().toLowerCase(Locale.ROOT);

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

    public String getKey() {
        return id.replace(" ", "");
    }

    public boolean getError() {
        return this.result.equalsIgnoreCase("error");
    }

    public boolean getWarning() {
        return this.result.equalsIgnoreCase("warning");
    }
}
