package com.endava.cats.model.report;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class CatsTestCaseSummary implements Comparable<CatsTestCaseSummary> {
    private String scenario;
    private String result;
    private String id;
    private String fuzzer;
    private String path;

    public static CatsTestCaseSummary fromCatsTestCase(String id, CatsTestCase testCase) {
        CatsTestCaseSummary summary = new CatsTestCaseSummary();
        summary.id = id;
        summary.scenario = testCase.getScenario();
        summary.result = testCase.getResult();
        summary.fuzzer = testCase.getFuzzer();
        summary.path = testCase.getPath();

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
}
