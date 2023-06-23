package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

@Singleton
@HeaderFuzzer
@EmojiFuzzer
public class LeadingMultiCodePointEmojisInHeadersFuzzer extends BaseHeadersFuzzer {

    public LeadingMultiCodePointEmojisInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .typeOfDataSentToTheService("values prefixed with multi code point emojis")
                .fuzzStrategy(UnicodeGenerator.getMultiCodePointEmojis()
                        .stream().map(value -> FuzzingStrategy.prefix().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }
}
