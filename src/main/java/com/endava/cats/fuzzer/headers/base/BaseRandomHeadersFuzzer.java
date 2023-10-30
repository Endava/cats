package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class BaseRandomHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(BaseRandomHeadersFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final ProcessingArguments processingArguments;

    protected BaseRandomHeadersFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener, ProcessingArguments processingArguments) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.processingArguments = processingArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        List<CatsHeader> headers = new ArrayList<>(data.getHeaders());

        for (int i = 0; i < processingArguments.getRandomHeadersNumber(); i++) {
            headers.add(CatsHeader.builder()
                    .name(RandomStringUtils.randomAlphanumeric(10))
                    .required(false)
                    .value(this.randomHeadersValueFunction().apply(10)).build());
        }

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzingData(data)
                        .expectedSpecificResponseCode("4XX or 2XX")
                        .fuzzer(this)
                        .logger(logger)
                        .scenario("Add 10 000 extra random headers.")
                        .responseProcessor(this::checkResponse)
                        .headers(headers)
                        .build()
        );
    }

    public void checkResponse(CatsResponse response, FuzzingData data) {
        if (ResponseCodeFamily.is2xxCode(response.getResponseCode()) || ResponseCodeFamily.is4xxCode(response.getResponseCode())) {
            testCaseListener.reportResultInfo(logger, data, "Request returned as expected for http method [{}] with response code [{}]",
                    response.getHttpMethod(), response.getResponseCode());
        } else {
            testCaseListener.reportResultError(logger, data, "Unexpected Response Code: %s".formatted(response.getResponseCode()),
                    "Request failed unexpectedly for http method [{}]: expected [{}], actual [{}]", response.getHttpMethod(), "2XX or 4XX", response.getResponseCode());
        }
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    /**
     * Override this to provide a concrete implementation for generating headers value.
     *
     * @return the Function generating random header values
     */
    protected abstract Function<Integer, String> randomHeadersValueFunction();
}
