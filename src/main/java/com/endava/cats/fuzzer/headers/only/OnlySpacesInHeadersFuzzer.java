package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends only spaces in headers.
 */
@Singleton
@HeaderFuzzer
public class OnlySpacesInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new OnlySpacesInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    protected OnlySpacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.TWOXX)
                .typeOfDataSentToTheService("values replaced by spaces")
                .fuzzStrategy(UnicodeGenerator.getSpacesHeaders()
                        .stream().map(value -> FuzzingStrategy.replace().withData(value)).toList())
                .build();
    }

}
