package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.json.JsonUtils;
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
    private static final String CURL_TEMPLATE = """
            curl  -X %s \\
                 %s \\
                 %s \\
                  %s
            """;
    private static final String CURL_HEADER = " -H \"%s: %s\"";
    private static final String CURL_BODY = " -d '%s'";

    private String testId;
    private String scenario;
    private String expectedResult;
    private String result;
    private String resultReason;
    private String resultDetails;
    private CatsRequest request = CatsRequest.empty();
    private CatsResponse response = CatsResponse.empty();
    private String path = "####";
    private String fuzzer;
    private String fullRequestPath;
    private String contractPath;

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

    public String getCurl() {
        if (request.getHttpMethod().equals("####")) {
            return "";
        }

        StringBuilder headersString = new StringBuilder();
        String body = "";
        request.getHeaders().forEach(header -> headersString.append(CURL_HEADER.formatted(header.getKey(), header.getValue())));
        if (HttpMethod.requiresBody(request.getHttpMethod())) {
            body = CURL_BODY.formatted(request.getPayload());
        }

        return CURL_TEMPLATE.formatted(request.getHttpMethod(), headersString.toString(), body, fullRequestPath);
    }
}
