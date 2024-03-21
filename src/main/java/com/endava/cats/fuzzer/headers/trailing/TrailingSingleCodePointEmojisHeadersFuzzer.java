package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

/**
 * Fuzzer that trails headers with single conde point emojis.
 */
@Singleton
@HeaderFuzzer
@EmojiFuzzer
public class TrailingSingleCodePointEmojisHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new TrailingSingleCodePointEmojisHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    public TrailingSingleCodePointEmojisHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .typeOfDataSentToTheService("values suffixed with single code point emojis")
                .fuzzStrategy(UnicodeGenerator.getSingleCodePointEmojis()
                        .stream().map(value -> FuzzingStrategy.trail().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }

}