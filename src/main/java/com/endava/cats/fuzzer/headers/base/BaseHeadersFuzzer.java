package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutorContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

/**
 * Fuzzer that iterates through all headers and executes fuzzing according to a given {@link BaseHeadersFuzzerContext}.
 */
public abstract class BaseHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(this.getClass());

    private final HeadersIteratorExecutor headersIteratorExecutor;
    private BaseHeadersFuzzerContext fuzzerContext;

    protected BaseHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        this.headersIteratorExecutor = headersIteratorExecutor;
    }

    @Override
    public void fuzz(FuzzingData fuzzingData) {
        BaseHeadersFuzzerContext context = this.getFuzzerContext();
        headersIteratorExecutor.execute(
                HeadersIteratorExecutorContext.builder()
                        .fuzzer(this)
                        .logger(logger)
                        .expectedResponseCodeForOptionalHeaders(context.getExpectedHttpForOptionalHeadersFuzzed())
                        .expectedResponseCodeForRequiredHeaders(context.getExpectedHttpCodeForRequiredHeadersFuzzed())
                        .fuzzValueProducer(context::getFuzzStrategy)
                        .scenario("Send [%s] in headers.".formatted(context.getTypeOfDataSentToTheService()))
                        .matchResponseSchema(context.isMatchResponseSchema())
                        .fuzzingData(fuzzingData)
                        .build());
    }

    public BaseHeadersFuzzerContext getFuzzerContext() {
        if (fuzzerContext == null) {
            fuzzerContext = createFuzzerContext();
        }
        return fuzzerContext;
    }


    /**
     * Override this to provide details about Fuzzer expectations and fuzzing strategy.
     *
     * @return a context to be used to execute the fuzzing
     */
    public abstract BaseHeadersFuzzerContext createFuzzerContext();


    @Override
    public String description() {
        return "iterate through each header and send %s in the targeted header"
                .formatted(this.getFuzzerContext().getTypeOfDataSentToTheService());
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
