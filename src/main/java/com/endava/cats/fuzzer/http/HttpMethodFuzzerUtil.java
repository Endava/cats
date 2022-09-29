package com.endava.cats.fuzzer.http;

import com.endava.cats.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
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

    private final SimpleExecutor simpleExecutor;

    @Inject
    public HttpMethodFuzzerUtil(TestCaseListener tcl, SimpleExecutor se) {
        this.testCaseListener = tcl;
        this.simpleExecutor = se;
    }

    public void process(Fuzzer fuzzer, FuzzingData data, HttpMethod httpMethod) {
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .logger(logger)
                        .fuzzer(fuzzer)
                        .expectedSpecificResponseCode("405")
                        .payload(HttpMethod.requiresBody(httpMethod) ? data.getPayload() : "")
                        .scenario("Send a happy flow request with undocumented HTTP method: %s".formatted(httpMethod))
                        .responseProcessor(this::checkResponse)
                        .fuzzingData(data)
                        .httpMethod(httpMethod)
                        .build()
        );
    }

    public void checkResponse(CatsResponse response, FuzzingData data) {
        if (response.getResponseCode() == 405) {
            testCaseListener.reportResultInfo(logger, data, "Request failed as expected for http method [{}] with response code [{}]",
                    response.getHttpMethod(), response.getResponseCode());
        } else if (ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            testCaseListener.reportResultError("Unexpected Response Code: %s".formatted(response.getResponseCode()), logger, data, "Request succeeded unexpectedly for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), 405, response.getResponseCode());
        } else {
            testCaseListener.reportResultWarn("Unexpected Response Code: %s".formatted(response.getResponseCode()), logger, data, "Unexpected response code for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), 405, response.getResponseCode());
        }
    }
}
