package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.RuntimeResourcePool;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.PathItem;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;

/**
 * Runs after all fuzzers finished for a path and repeats the GET request of that path, but only if an unmodified
 * GET succeeded earlier in the run. A server error at this point suggests that fuzzed POST, PUT or PATCH requests
 * stored data which now breaks reads.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class CheckReadsStillWorkFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CheckReadsStillWorkFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final RuntimeResourcePool runtimeResourcePool;

    /**
     * Creates a new CheckReadsStillWorkFuzzer instance.
     *
     * @param simpleExecutor      the executor used to send the request
     * @param testCaseListener    the listener used to report results
     * @param runtimeResourcePool the pool holding the outcomes of unmodified requests sent earlier in the run
     */
    public CheckReadsStillWorkFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                     RuntimeResourcePool runtimeResourcePool) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.runtimeResourcePool = runtimeResourcePool;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!hasWriteOperations(data.getPathItem())) {
            logger.skip("Path {} has no POST, PUT or PATCH operations that could change what GET returns", data.getPath());
            return;
        }
        if (!runtimeResourcePool.hasSucceededWithUnmodifiedRequest(data.getPath(), HttpMethod.GET)) {
            logger.skip("GET {} did not succeed with an unmodified request earlier in the run, so there is nothing to compare against", data.getPath());
            return;
        }
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzingData(data)
                .fuzzer(this)
                .payload(data.getPayload())
                .scenario("Repeat a GET request that succeeded before fuzzing to check that reads still work after the path was fuzzed")
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .responseProcessor(this::checkResponse)
                .build());
    }

    void checkResponse(CatsResponse response, FuzzingData data) {
        int responseCode = response.getResponseCode();
        if (ResponseCodeFamily.is5xxCode(responseCode)) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.READ_BROKEN_AFTER_FUZZING.value(),
                    "GET succeeded with unmodified requests earlier in the run, but returns {} after the path was fuzzed. Fuzzed writes might have stored data that breaks reads",
                    response.responseCodeAsString());
        } else if (responseCode == 404 || responseCode == 410) {
            testCaseListener.reportResultInfo(logger, data,
                    "Resource no longer available after fuzzing (response code {}). It was most likely deleted by a fuzzer",
                    response.responseCodeAsString());
        } else {
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }
    }

    private boolean hasWriteOperations(PathItem pathItem) {
        return pathItem == null || pathItem.getPost() != null || pathItem.getPut() != null || pathItem.getPatch() != null;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.stream(HttpMethod.values()).filter(method -> method != HttpMethod.GET).toList();
    }

    @Override
    public String description() {
        return "repeat GET requests that succeeded before fuzzing, after all fuzzers finished for a path, to detect fuzzed writes that break reads";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
