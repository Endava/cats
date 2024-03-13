package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.Collections;


/**
 * Fuzzer sending very large strings in headers.
 */
@Singleton
@HeaderFuzzer
public class VeryLargeStringsInHeadersFuzzer extends BaseHeadersFuzzer {
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new instance.
     *
     * @param headersIteratorExecutor executor used to run the fuzz logic
     * @param pa                      used to get the size of the strings
     */
    public VeryLargeStringsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor, ProcessingArguments pa) {
        super(headersIteratorExecutor);
        this.processingArguments = pa;
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .typeOfDataSentToTheService("large values")
                .fuzzStrategy(Collections.singletonList(
                        FuzzingStrategy.replace().withData(
                                StringGenerator.generateLargeString(processingArguments.getLargeStringsSize() / 4))))
                .matchResponseSchema(false)
                .matchResponseContentType(false)
                .build();
    }
}
