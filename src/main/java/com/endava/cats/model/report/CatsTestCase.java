package com.endava.cats.model.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(of = "scenario")
public class CatsTestCase {
    private String testId;
    private String scenario;
    private String expectedResult;
    private String result;
    private String resultDetails;
    private CatsRequest request;
    private CatsResponse response;
    private String path;
    private String fuzzer;
    private String fullRequestPath;

    public String executionTimeString() {
        return testId + " - " + response.getResponseTimeInMs() + "ms";
    }

    public boolean isNotSkipped() {
        return !"skipped".equalsIgnoreCase(result);
    }

    public boolean notIgnoredForExecutionStatistics() {
        return !"SKIPPED".equalsIgnoreCase(response.getHttpMethod());
    }

}
