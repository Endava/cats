package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.Collections;

/**
 * Sends zalgo text in headers.
 */
@HeaderFuzzer
@Singleton
public class ZalgoTextInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new instance.
     *
     * @param headersIteratorExecutor executor used to execute the fuzz logic
     */
    public ZalgoTextInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .typeOfDataSentToTheService("zalgo text")
                .fuzzStrategy(Collections.singletonList(FuzzingStrategy.replace().withData(UnicodeGenerator.getZalgoText())))
                .matchResponseSchema(false)
                .build();
    }
}