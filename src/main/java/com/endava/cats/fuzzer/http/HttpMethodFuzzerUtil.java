package com.endava.cats.fuzzer.http;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HttpMethodFuzzerUtil {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(HttpMethodFuzzerUtil.class);
    private final TestCaseListener testCaseListener;

    private final ServiceCaller serviceCaller;

    @Inject
    public HttpMethodFuzzerUtil(TestCaseListener tcl, ServiceCaller sc) {
        this.testCaseListener = tcl;
        this.serviceCaller = sc;
    }

    public void process(FuzzingData data, HttpMethod httpMethod) {
        testCaseListener.addScenario(logger, "Send a happy flow request with undocumented HTTP method: {}", httpMethod);
        testCaseListener.addExpectedResult(logger, "Should get a 405 response code");
        String payload = HttpMethod.requiresBody(httpMethod) ? data.getPayload() : "";
        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(payload).httpMethod(httpMethod).contentType(data.getFirstRequestContentType()).build());
        this.checkResponse(data, response);
    }

    public void checkResponse(FuzzingData data, CatsResponse response) {
        if (response.getResponseCode() == 405) {
            testCaseListener.reportResultInfo(logger, data, "Request failed as expected for http method [{}] with response code [{}]",
                    response.getHttpMethod(), response.getResponseCode());
        } else if (ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            testCaseListener.reportResultError(logger, data, "Request succeeded unexpectedly for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), 405, response.getResponseCode());
        } else {
            testCaseListener.reportResultWarn(logger, data, "Unexpected response code for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), 405, response.getResponseCode());
        }
    }
}
