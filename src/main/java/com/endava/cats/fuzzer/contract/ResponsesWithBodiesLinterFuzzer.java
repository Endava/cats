package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * Checks if the http response codes have a response body.
 */
@Singleton
@LinterFuzzer
public class ResponsesWithBodiesLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new ResponsesWithBodiesLinterFuzzer instance.
     *
     * @param tcl the test case listener
     */
    public ResponsesWithBodiesLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if all http response codes for HTTP method {} have a response body", data.getMethod());
        testCaseListener.addExpectedResult(log, "All HTTP response codes (except for 204 and 304) have a response body");

        List<String> httpResponseCodesMissingBody = data.getResponses()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();

        if (httpResponseCodesMissingBody.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All HTTP response codes have a response body");
        } else {
            testCaseListener.reportResultError(log, data, "Missing response body for some HTTP response codes", "The following HTTP response codes are missing a response body: {}", httpResponseCodesMissingBody);
        }
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
