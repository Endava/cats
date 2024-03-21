package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Fuzzer that trails headers with control chars.
 */
@Singleton
@HeaderFuzzer
@ControlCharFuzzer
public class TrailingControlCharsInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new TrailingControlCharsInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    public TrailingControlCharsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.FOURXX)
                .typeOfDataSentToTheService("values suffixed with control chars")
                .fuzzStrategy(getInvisibleChars().stream().map(value -> FuzzingStrategy.trail().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }

    public List<String> getInvisibleChars() {
        List<String> controlChars = new ArrayList<>(UnicodeGenerator.getControlCharsHeaders());
        controlChars.remove("\r");

        return controlChars;
    }
}