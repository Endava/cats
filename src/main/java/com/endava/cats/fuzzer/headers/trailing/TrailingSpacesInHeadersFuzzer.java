package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

/**
 * Fuzzer that adds trailing spaces into the headers.
 */
@Singleton
@HeaderFuzzer
public class TrailingSpacesInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new TrailingSpacesInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    protected TrailingSpacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.TWOXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.TWOXX)
                .typeOfDataSentToTheService("values suffixed with spaces")
                .fuzzStrategy(UnicodeGenerator.getSpacesHeaders()
                        .stream().map(value -> FuzzingStrategy.trail().withData(value)).toList())
                .build();
    }
}
