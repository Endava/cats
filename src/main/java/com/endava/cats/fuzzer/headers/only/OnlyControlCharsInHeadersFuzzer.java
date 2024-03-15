package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends only control chars characters in headers.
 */
@Singleton
@HeaderFuzzer
@ControlCharFuzzer
public class OnlyControlCharsInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new OnlyControlCharsInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    public OnlyControlCharsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .typeOfDataSentToTheService("values replaced by control chars")
                .fuzzStrategy(UnicodeGenerator.getControlCharsHeaders()
                        .stream().map(value -> FuzzingStrategy.replace().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }
}
