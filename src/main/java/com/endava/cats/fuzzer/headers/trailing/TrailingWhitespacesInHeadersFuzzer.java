package com.endava.cats.fuzzer.headers.trailing;

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
 * Fuzzer that adds trailing whitespaces in headers.
 */
@Singleton
@HeaderFuzzer
@WhitespaceFuzzer
public class TrailingWhitespacesInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new TrailingWhitespacesInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    public TrailingWhitespacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .typeOfDataSentToTheService("values suffixed with unicode separators")
                .fuzzStrategy(UnicodeGenerator.getSeparatorsHeaders()
                        .stream().map(value -> FuzzingStrategy.trail().withData(value)).toList())
                .build();
    }
}
