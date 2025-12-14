package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.KeyValuePair;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Utility class executing common logic for fuzzers sending undocumented HTTP methods.
 */
@Singleton
public class HttpMethodFuzzerUtil {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(HttpMethodFuzzerUtil.class);
    private final TestCaseListener testCaseListener;

    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new HttpMethodFuzzerUtil instance.
     *
     * @param tcl the test case listener
     * @param se  the executor
     */
    @Inject
    public HttpMethodFuzzerUtil(TestCaseListener tcl, SimpleExecutor se) {
        this.testCaseListener = tcl;
        this.simpleExecutor = se;
    }

    /**
     * Processes fuzzing for a specific HTTP method using the provided fuzzer and FuzzingData.
     *
     * <p>This method utilizes a SimpleExecutor to execute fuzzing based on the given fuzzer, FuzzingData,
     * and HTTP method. It configures the execution context with the necessary parameters, including the logger,
     * expected response code, payload, scenario description, response processor, and additional fuzzing-related details.</p>
     *
     * @param fuzzer     The Fuzzer instance responsible for generating test cases and payloads during fuzzing.
     * @param data       The FuzzingData containing information about the path, method, payload, and headers.
     * @param httpMethod The HTTP method for which fuzzing is being performed.
     */
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

    private void checkResponse(CatsResponse response, FuzzingData data) {
        if (response.getResponseCode() == 405) {
            this.handle405(response, data);
        } else if (ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            testCaseListener.reportResultError(logger, data, "Unexpected response code: %s".formatted(response.getResponseCode()), "Request succeeded unexpectedly for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), 405, response.getResponseCode());
        } else {
            testCaseListener.reportResultWarn(logger, data, "Unexpected response code: %s".formatted(response.getResponseCode()), "Unexpected response code for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), 405, response.getResponseCode());
        }
    }

    private void handle405(CatsResponse response, FuzzingData data) {
        KeyValuePair<String, String> allowHeader = response.getHeader("Allow");
        if (allowHeader == null) {
            testCaseListener.reportResultWarn(logger, data, "Missing Allowed header", "Request failed as expected for http method [{}] with response code [{}], but missing Allow header", response.getHttpMethod(), response.getResponseCode());
        } else if (allowHeader.getValue().contains(response.getHttpMethod())) {
            testCaseListener.reportResultWarn(logger, data, "Wrong Allowed header", "Request failed as expected for http method [{}] with response code [{}], but Allow header contains [{}]", response.getHttpMethod(), response.getResponseCode(), response.getHttpMethod());
        } else {
            testCaseListener.reportResultInfo(logger, data, "Request failed as expected for http method [{}] with response code [{}]",
                    response.getHttpMethod(), response.getResponseCode());
        }
    }
}
