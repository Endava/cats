package com.endava.cats.fuzzer.headers.leading;

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
 * Fuzzer that prefixes headers with single code point emojis.
 */
@Singleton
@HeaderFuzzer
@EmojiFuzzer
public class LeadingSingleCodePointEmojisInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new LeadingSingleCodePointEmojisInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    public LeadingSingleCodePointEmojisInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .typeOfDataSentToTheService("values prefixed with single code point emojis")
                .fuzzStrategy(UnicodeGenerator.getSingleCodePointEmojis()
                        .stream().map(value -> FuzzingStrategy.prefix().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }
}
