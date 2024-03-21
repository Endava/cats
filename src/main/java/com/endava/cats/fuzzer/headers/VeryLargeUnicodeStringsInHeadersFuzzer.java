package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

/**
 * Sends very large unicode strings into headers.
 */
@Singleton
@HeaderFuzzer
public class VeryLargeUnicodeStringsInHeadersFuzzer extends BaseHeadersFuzzer {
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new instance.
     *
     * @param headersIteratorExecutor the executor used to run the fuzz logic
     * @param pa                      used to get the size of the strings
     */
    public VeryLargeUnicodeStringsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor, ProcessingArguments pa) {
        super(headersIteratorExecutor);
        this.processingArguments = pa;
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .typeOfDataSentToTheService("large unicode values")
                .fuzzStrategy(FuzzingStrategy.getLargeValuesStrategy(processingArguments.getLargeStringsSize()))
                .matchResponseSchema(false)
                .matchResponseContentType(false)
                .build();
    }

}