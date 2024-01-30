package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

/**
 * Fuzzer that prefixes headers with control chars.
 */
@Singleton
@HeaderFuzzer
@ControlCharFuzzer
public class LeadingControlCharsInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new LeadingControlCharsInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    public LeadingControlCharsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .typeOfDataSentToTheService("values prefixed with control chars")
                .fuzzStrategy(UnicodeGenerator.getControlCharsHeaders()
                        .stream().map(value -> FuzzingStrategy.prefix().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }

}
