package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.strategy.FuzzingStrategy;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
public class VeryLargeUnicodeStringsInHeadersFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {
    private final ProcessingArguments processingArguments;

    public VeryLargeUnicodeStringsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor, ProcessingArguments pa) {
        super(headersIteratorExecutor);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "large unicode values";
    }

    @Override
    protected List<FuzzingStrategy> fuzzStrategy() {
        return FuzzingStrategy.getLargeValuesStrategy(processingArguments.getLargeStringsSize());
    }

    @Override
    public String description() {
        return "iterate through each header and send large unicode values in the targeted header";
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}