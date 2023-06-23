package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.stream.Stream;

@Singleton
@HeaderFuzzer
public class CRLFHeadersFuzzer extends BaseHeadersFuzzer {
    protected CRLFHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .typeOfDataSentToTheService("CR & LF characters")
                .fuzzStrategy(Stream.of("\r\n").map(value -> FuzzingStrategy.replace().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }
}
