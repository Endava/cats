package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.ann.Exclude;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.SimpleJsonFormatter;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Entity holding a CATS test case.
 */
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

    /**
     * Constant marking test case which are skipped from reporting.
     */
    public static final String SKIP_REPORTING = "skip_reporting";

    private String testId;
    private String traceId = UUID.randomUUID().toString();
    private String scenario;
    private String expectedResult;
    private String result;
    private String resultReason;
    private String resultDetails;
    private String resultIgnoreDetails;
    private CatsRequest request = CatsRequest.empty();
    private CatsResponse response = CatsResponse.empty();
    private String path = "####";
    private String fuzzer;
    private String fullRequestPath;
    private String contractPath;
    private String server;

    @Exclude
    private boolean validJson = true;

    @Exclude
    private boolean js;

    @Exclude
    private Gson maskingSerializer;

    @Exclude
    private List<String> errorLeaks = List.of();

    /**
     * Checks if the test result is not marked as skipped.
     *
     * @return True if the result is not skipped, false otherwise.
     */
    public boolean isNotSkipped() {
        return !SKIPPED.equalsIgnoreCase(result) && !SKIP_REPORTING.equalsIgnoreCase(result);
    }

    /**
     * Records a result ignore reason based on the {@code from} and {@code to} levels.
     *
     * @param from original reporting level
     */
    public void setResultIgnoreDetails(String from) {
        this.resultIgnoreDetails = "Result switched from %s to SUCCESS based on --ignoreXXX arguments".formatted(from);
    }

    /**
     * Marks test case as SKIPPED.
     */
    public void setResultSkipped() {
        this.result = SKIPPED;
    }

    /**
     * Checks if the current test case has a valid http response code.
     *
     * @return true if the test case has a valid http code, false otherwise
     */
    public boolean notIgnoredForExecutionStatistics() {
        return response.isValidErrorCode();
    }

    /**
     * Checks if the current test case has a valid http response code.
     *
     * @return true if the test case has a valid http code, false otherwise
     */
    public boolean hasRequestDetails() {
        return response.isValidErrorCode();
    }

    /**
     * Checks if the current test case has a result that was switched based on --ignoreXX arguments.
     *
     * @return true if the result was switched, false otherwise
     */
    public boolean hasResultSwitched() {
        return resultIgnoreDetails != null;
    }

    /**
     * A json formatted version of the http headers sent in request
     *
     * @return request headers in json format
     */
    public String getHeaders() {
        return maskingSerializer.toJson(request.getHeaders());
    }

    /**
     * Well formatted json if request is a valid json or just the payload string otherwise
     *
     * @return the request payload
     */
    public String getRequestJson() {
        if (!validJson || !JsonUtils.isValidJson(request.getPayload())) {
            return SimpleJsonFormatter.formatJson(request.getPayload());
        }

        JsonReader reader = new JsonReader(new StringReader(request.getPayload()));
        reader.setStrictness(Strictness.LENIENT);
        return maskingSerializer.toJson(JsonParser.parseReader(reader));
    }

    /**
     * Serializes the response object to JSON format using a masking serializer.
     *
     * @return The JSON representation of the response object with applied masking.
     */
    public String getResponseJson() {
        return maskingSerializer.toJson(response);
    }

    /**
     * Lowercase version of http method.
     *
     * @return lower case http method
     */
    public String getHttpMethod() {
        return String.valueOf(request.getHttpMethod()).toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the curl equivalent of running the test case.
     *
     * @return curl equivalent of running the test case
     */
    public String getCurl() {
        if (request.getHttpMethod().equals("####")) {
            return "";
        }

        StringBuilder headersString = new StringBuilder();
        String body = "";
        request.getHeaders().forEach(header -> headersString.append(CURL_HEADER.formatted(header.getKey(), this.getHeaderValueForCurl(header))));

        if (HttpMethod.requiresBody(request.getHttpMethod())) {
            body = CURL_BODY.formatted(request.getPayload());
        }

        return CURL_TEMPLATE.formatted(request.getHttpMethod(), headersString.toString(), body, fullRequestPath);
    }

    /**
     * CATS uses $$variable to refer to env variables, while curl needs $variable.
     * We make sure that when masking is done, the header from curl command uses a single $.
     *
     * @param header the current header being processed
     * @return a header value that passed through masking
     */
    private Object getHeaderValueForCurl(KeyValuePair<String, Object> header) {
        String value = String.valueOf(JsonUtils.getVariableFromJson(maskingSerializer.toJson(header), "value"));

        return value.startsWith("$$") ? value.substring(1) : header.getValue();
    }

    /**
     * Returns the cats replay command
     *
     * @return cats replay command
     */
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

    public String getResultDetailsForHtml() {
        return this.resultDetails.replace("\n", "<br>")
                .replace("\r", "<br>");
    }
}
