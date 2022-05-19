package com.endava.cats.model.report;

import lombok.EqualsAndHashCode;
import lombok.Getter;

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

    public static CatsTestCaseSummary fromCatsTestCase(String id, CatsTestCase testCase) {
        CatsTestCaseSummary summary = new CatsTestCaseSummary();
        summary.id = id;
        summary.scenario = testCase.getScenario();
        summary.result = testCase.getResult();
        summary.fuzzer = testCase.getFuzzer();
        summary.path = testCase.getPath();
        summary.resultReason = testCase.getResultReason();
        summary.resultDetails = testCase.getResultDetails();
        summary.timeToExecuteInSec = testCase.getResponse().getResponseTimeInMs() / 1000d;

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
