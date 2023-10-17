package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.json.JsonUtils;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.io.StringReader;
import java.util.Locale;

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

    private static final String CATS_REPLAY = "cats replay %s";
    private static final String SKIPPED = "skipped";
    public static final String SKIP_REPORTING = "skip_reporting";

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
    private String server;

    private boolean js;

    public boolean isNotSkipped() {
        return !SKIPPED.equalsIgnoreCase(result) && !SKIP_REPORTING.equalsIgnoreCase(result);
    }

    public void setResultSkipped() {
        this.result = SKIPPED;
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

    public String getHttpMethod() {
        return String.valueOf(request.getHttpMethod()).toLowerCase(Locale.ROOT);
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

    public String getCatsReplay() {
        return CATS_REPLAY.formatted(testId.replace(" ", ""));
    }

    /**
     * Updates the fullRequest path and request#url with the new server.
     *
     * @param server the url where the service is deployed
     */
    public void updateServer(String server) {
        if (StringUtils.isBlank(server)) {
            return;
        }
        this.fullRequestPath = this.fullRequestPath.replace(this.server, server);
        request.setUrl(request.getUrl().replace(this.server, server));
    }
}
