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

/**
 * Base class for random headers fuzzers.
 */
public abstract class BaseRandomHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(BaseRandomHeadersFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    protected final ProcessingArguments processingArguments;

    /**
     * Constructs a new instance of BaseRandomHeadersFuzzer with protected access.
     *
     * <p>This base fuzzer is intended for fuzzing scenarios involving random headers.
     * It utilizes a SimpleExecutor for executing fuzzing, relies on a TestCaseListener for handling test case events,
     * and takes ProcessingArguments into account for additional processing.</p>
     *
     * @param simpleExecutor      The SimpleExecutor responsible for executing fuzzing with random headers.
     * @param testCaseListener    The TestCaseListener instance responsible for handling test case events.
     * @param processingArguments The ProcessingArguments containing additional parameters for header fuzzing.
     */
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
                        .expectedResponseCode(ResponseCodeFamily.FOURXX)
                        .fuzzer(this)
                        .logger(logger)
                        .scenario(String.format("Add %s extra random headers.", processingArguments.getRandomHeadersNumber()))
                        .responseProcessor(this::checkResponse)
                        .headers(headers)
                        .build()
        );
    }

    private void checkResponse(CatsResponse response, FuzzingData data) {
        if (ResponseCodeFamily.FOURXX.matchesAllowedResponseCodes(String.valueOf(response.getResponseCode()))) {
            testCaseListener.reportResultInfo(logger, data, "Request returned as expected for http method [{}] with response code [{}]",
                    response.getHttpMethod(), response.getResponseCode());
        } else {
            testCaseListener.reportResultError(logger, data, "Unexpected Response Code: %s".formatted(response.getResponseCode()),
                    "Request failed unexpectedly for http method [{}]: expected [{}], actual [{}]", response.getHttpMethod(),
                    ResponseCodeFamily.FOURXX.allowedResponseCodes(), response.getResponseCode());
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
