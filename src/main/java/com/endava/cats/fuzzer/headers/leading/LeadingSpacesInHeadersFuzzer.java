package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
@HeaderFuzzer
public class LeadingSpacesInHeadersFuzzer extends BaseHeadersFuzzer {

    protected LeadingSpacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext getFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.TWOXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.TWOXX)
                .typeOfDataSentToTheService("values prefixed with spaces")
                .fuzzStrategy(getInvisibleChars()
                        .stream().map(value -> FuzzingStrategy.prefix().withData(value)).toList())
                .build();
    }

    public List<String> getInvisibleChars() {
        List<String> leadingChars = new ArrayList<>(UnicodeGenerator.getSpacesHeaders());
        leadingChars.remove("\r");
        return leadingChars;
    }
}
