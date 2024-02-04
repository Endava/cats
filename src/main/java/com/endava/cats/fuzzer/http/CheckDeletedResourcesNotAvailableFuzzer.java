package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;

/**
 * Fuzzer that checks if deleted resources are still available.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class CheckDeletedResourcesNotAvailableFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CheckDeletedResourcesNotAvailableFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final CatsGlobalContext catsGlobalContext;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new CheckDeletedResourcesNotAvailableFuzzer instance.
     *
     * @param simpleExecutor    the executor
     * @param catsGlobalContext the cats global context
     * @param testCaseListener  the test case listener
     */
    public CheckDeletedResourcesNotAvailableFuzzer(SimpleExecutor simpleExecutor, CatsGlobalContext catsGlobalContext, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.catsGlobalContext = catsGlobalContext;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getMethod() != HttpMethod.GET) {
            return;
        }

        logger.info("Stored successful DELETE requests: {}", catsGlobalContext.getSuccessfulDeletes().size());
        for (String delete : catsGlobalContext.getSuccessfulDeletes()) {
            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .logger(logger)
                            .fuzzer(this)
                            .expectedResponseCode(ResponseCodeFamily.FOURXX)
                            .fuzzingData(data)
                            .payload("{}")
                            .path(getRelativePath(delete))
                            .scenario("Check that previously deleted resource is not available")
                            .responseProcessor(this::checkResponse)
                            .build()
            );
        }
        catsGlobalContext.getSuccessfulDeletes().clear();
    }

    private void checkResponse(CatsResponse response, FuzzingData data) {
        if (response.getResponseCode() == 404 || response.getResponseCode() == 410) {
            testCaseListener.reportResultInfo(logger, data, "Request failed as expected for http method [{}] with response code [{}]",
                    response.getHttpMethod(), response.getResponseCode());
        } else {
            testCaseListener.reportResultError(logger, data, "Unexpected Response Code: %s".formatted(response.getResponseCode()), "Request succeeded unexpectedly for http method [{}]: expected [{}], actual [{}]",
                    data.getMethod(), "404, 410", response.responseCodeAsString());
        }
    }

    static String getRelativePath(String url) {
        try {
            return URI.create(url).toURL().getPath();
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.PATCH, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "checks that resources are not available after successful deletes";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
