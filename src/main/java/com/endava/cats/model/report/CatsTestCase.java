package com.endava.cats.model.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.util.JsonUtils;
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

    public boolean isNotSkipped() {
        return !"skipped".equalsIgnoreCase(result);
    }

    public boolean notIgnoredForExecutionStatistics() {
        return response.getResponseCode() != 999;
    }

    public String getHeaders() {
        return JsonUtils.GSON.toJson(request.getHeaders());
    }

    public String getRequestJson() {
        if (JsonUtils.isValidJson(request.getPayload())) {
            JsonReader reader = new JsonReader(new StringReader(request.getPayload()));
            reader.setLenient(true);
            return JsonUtils.GSON.toJson(JsonParser.parseReader(reader));
        }
        return request.getPayload();
    }

    public String getResponseJson() {
        return JsonUtils.GSON.toJson(response);
    }
}
