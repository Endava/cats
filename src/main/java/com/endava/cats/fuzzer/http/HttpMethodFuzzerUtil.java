package com.endava.cats.fuzzer.http;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;

@Singleton
public class HttpMethodFuzzerUtil {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(HttpMethodFuzzerUtil.class);
    private final TestCaseListener testCaseListener;

    @Inject
    public HttpMethodFuzzerUtil(TestCaseListener tcl) {
        this.testCaseListener = tcl;
    }

    public void process(FuzzingData data, Function<ServiceData, CatsResponse> f, HttpMethod httpMethod) {
        testCaseListener.addScenario(LOGGER, "Send a happy flow request with undocumented HTTP method: {}", httpMethod);
        testCaseListener.addExpectedResult(LOGGER, "Should get a 405 response code");
        String payload = HttpMethod.requiresBody(httpMethod) ? data.getPayload() : "";
        CatsResponse response = f.apply(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(payload).httpMethod(httpMethod).contentType(data.getFirstRequestContentType()).build());
        this.checkResponse(response);
    }

    public void checkResponse(CatsResponse response) {
        if (response.getResponseCode() == 405) {
            testCaseListener.reportInfo(LOGGER, "Request failed as expected for http method [{}] with response code [{}]",
                    response.getHttpMethod(), response.getResponseCode());
        } else if (ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            testCaseListener.reportError(LOGGER, "Request succeeded unexpectedly for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), 405, response.getResponseCode());
        } else {
            testCaseListener.reportWarn(LOGGER, "Unexpected response code for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), 405, response.getResponseCode());
        }
    }
}
