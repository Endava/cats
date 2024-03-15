package com.endava.cats.fuzzer.headers.leading;

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
 * Fuzzer that prefixes headers with spaces.
 */
@Singleton
@HeaderFuzzer
public class LeadingSpacesInHeadersFuzzer extends BaseHeadersFuzzer {

    /**
     * Creates a new LeadingSpacesInHeadersFuzzer instance.
     *
     * @param headersIteratorExecutor the executor
     */
    protected LeadingSpacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamilyPredefined.TWOXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamilyPredefined.TWOXX)
                .typeOfDataSentToTheService("values prefixed with spaces")
                .fuzzStrategy(getInvisibleChars()
                        .stream().map(value -> FuzzingStrategy.prefix().withData(value)).toList())
                .build();
    }

    /**
     * Returns a list of invisible chars to be used for fuzzing.
     *
     * @return a list of values to be used for fuzzing
     */
    public List<String> getInvisibleChars() {
        List<String> leadingChars = new ArrayList<>(UnicodeGenerator.getSpacesHeaders());
        leadingChars.remove("\r");
        return leadingChars;
    }
}
