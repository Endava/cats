package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.WhitespaceFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends only whitespaces in headers.
 */
@Singleton
@HeaderFuzzer
@WhitespaceFuzzer
public class OnlyWhitespacesInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new OnlyWhitespacesInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    public OnlyWhitespacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .typeOfDataSentToTheService("values replaced by unicode separators")
                .fuzzStrategy(UnicodeGenerator.getSeparatorsHeaders()
                        .stream().map(value -> FuzzingStrategy.replace().withData(value)).toList())
                .build();
    }
}
