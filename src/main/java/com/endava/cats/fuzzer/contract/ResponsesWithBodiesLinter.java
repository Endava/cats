package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.fuzzer.contract.util.LinterVocabulary;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.OpenApiUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks if the http response codes have a response body.
 */
@Singleton
@Linter
public class ResponsesWithBodiesLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new ResponsesWithBodiesLinter instance.
     *
     * @param tcl the test case listener
     */
    public ResponsesWithBodiesLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if all http response codes for HTTP method {} have a response body", data.getMethod());
        testCaseListener.addExpectedResult(log, "All HTTP response codes (except for 1xx, 204, 205, 3xx redirects, and 201/202 with Location header) have a response body");

        List<String> httpResponseCodesMissingBody = data.getResponses()
                .entrySet()
                .stream()
                .filter(entry -> !LinterVocabulary.isBodylessStatus(entry.getKey()))
                .filter(entry -> entry.getValue().isEmpty() && !hasBodyOrLocationInOpenApi(data, entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();

        if (httpResponseCodesMissingBody.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All HTTP response codes have a response body");
        } else {
            testCaseListener.reportResultError(log, data, "Missing response body for some HTTP response codes", "The following HTTP response codes are missing a response body: {}", httpResponseCodesMissingBody);
        }
    }

    private static boolean hasBodyOrLocationInOpenApi(FuzzingData data, String code) {
        if (LinterVocabulary.LOCATION_ALLOWED_BODYLESS_CODES.contains(code)
                && LinterVocabulary.hasLocationHeader(data.getResponseHeaders().get(code))) {
            return true;
        }
        if (data.getPathItem() == null || data.getMethod() == null) {
            return false;
        }
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());
        if (operation == null || operation.getResponses() == null) {
            return false;
        }
        ApiResponse response = OpenApiUtils.resolveApiResponse(operation.getResponses().get(code), data.getOpenApi());
        if (response == null) {
            return false;
        }
        if (LinterVocabulary.LOCATION_ALLOWED_BODYLESS_CODES.contains(code) && response.getHeaders() != null
                && LinterVocabulary.hasLocationHeader(response.getHeaders().keySet())) {
            return true;
        }
        if (response.getContent() == null || response.getContent().isEmpty()) {
            return false;
        }
        return response.getContent().entrySet().stream()
                .anyMatch(entry -> hasDefinedPayload(entry.getKey(), entry.getValue()));
    }

    private static boolean hasDefinedPayload(String contentType, MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        if (mediaType.getSchema() != null || mediaType.getExample() != null || mediaType.getExamples() != null) {
            return true;
        }
        return LinterVocabulary.isBinaryOrStreamContentType(contentType);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD);
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that HTTP response codes (except for 204 and 304) have a response body";
    }
}
