package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.Expect4XXBaseHeadersFuzzer;
import com.endava.cats.strategy.FuzzingStrategy;

import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.List;

@Singleton
@HeaderFuzzer
public class EmptyStringsInHeadersFuzzer extends Expect4XXBaseHeadersFuzzer {

    public EmptyStringsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "empty values";
    }

    @Override
    protected List<FuzzingStrategy> fuzzStrategy() {
        return Collections.singletonList(FuzzingStrategy.replace().withData(""));
    }

    @Override
    public String description() {
        return "iterate through each header and send empty String values in the targeted header";
    }
}
