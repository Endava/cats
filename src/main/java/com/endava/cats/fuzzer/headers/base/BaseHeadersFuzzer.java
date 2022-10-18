package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.List;

public abstract class BaseHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(this.getClass());

    private final HeadersIteratorExecutor headersIteratorExecutor;

    protected BaseHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        this.headersIteratorExecutor = headersIteratorExecutor;
    }

    @Override
    public void fuzz(FuzzingData fuzzingData) {
        headersIteratorExecutor.execute(
                HeadersIteratorExecutorContext.builder()
                        .fuzzer(this)
                        .logger(logger)
                        .expectedResponseCodeForOptionalHeaders(this.getExpectedHttpForOptionalHeadersFuzzed())
                        .expectedResponseCodeForRequiredHeaders(this.getExpectedHttpCodeForRequiredHeadersFuzzed())
                        .fuzzValueProducer(this::fuzzStrategy)
                        .scenario("Send [%s] in headers.".formatted(this.typeOfDataSentToTheService()))
                        .matchResponseSchema(this.matchResponseSchema())
                        .fuzzingData(fuzzingData)
                        .build());
    }

    /**
     * Short description of data that is sent to the service.
     *
     * @return a short description
     */
    protected abstract String typeOfDataSentToTheService();

    /**
     * What is the expected HTTP Code when required headers are fuzzed with invalid values
     *
     * @return expected HTTP code
     */
    protected abstract ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed();

    /**
     * What is the expected HTTP code when optional headers are fuzzed with invalid values
     *
     * @return expected HTTP code
     */
    protected abstract ResponseCodeFamily getExpectedHttpForOptionalHeadersFuzzed();

    /**
     * What is the Fuzzing strategy the current Fuzzer will apply
     *
     * @return expected FuzzingStrategy
     */
    protected abstract List<FuzzingStrategy> fuzzStrategy();

    /**
     * There is a special case when we send Control Chars in Headers and an error (due to HTTP RFC specs)
     * is returned by the app server itself, not the application. In this case we don't want to check
     * if there is even a response body as the error page/response is served by the server, not the application layer.
     *
     * @return true if it should match response schema and false otherwise
     */
    public boolean matchResponseSchema() {
        return true;
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
