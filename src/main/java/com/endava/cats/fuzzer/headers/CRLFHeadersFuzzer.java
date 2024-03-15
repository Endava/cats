package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.stream.Stream;

/**
 * Fuzzes HTTP headers by injecting CR and LF characters.
 */
@Singleton
@HeaderFuzzer
public class CRLFHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new instance.
     *
     * @param headersIteratorExecutor executor used to run the fuzzing logic
     */
    protected CRLFHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .typeOfDataSentToTheService("CR & LF characters")
                .fuzzStrategy(Stream.of("\r\n").map(value -> FuzzingStrategy.replace().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }
}
