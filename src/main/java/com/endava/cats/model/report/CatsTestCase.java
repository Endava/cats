package com.endava.cats.model.report;

import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.StringReader;

@Getter
@Setter
@ToString(of = "scenario")
public class CatsTestCase {
    private String testId;
    private String scenario;
    private String expectedResult;
    private String result;
    private String resultReason;
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

    public String getHeaders() {
        return TestCaseExporter.GSON.toJson(request.getHeaders());
    }

    public String getRequestJson() {
        JsonReader reader = new JsonReader(new StringReader(request.getPayload()));
        reader.setLenient(true);
        return TestCaseExporter.GSON.toJson(JsonParser.parseReader(reader));
    }

    public String getResponseJson() {
        return TestCaseExporter.GSON.toJson(response);
    }
}
