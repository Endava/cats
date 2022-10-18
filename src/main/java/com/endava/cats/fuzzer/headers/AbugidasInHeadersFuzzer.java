package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.generator.simple.PayloadGenerator;

import javax.inject.Singleton;
import java.util.List;

@HeaderFuzzer
@Singleton
public class AbugidasInHeadersFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {

    public AbugidasInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "abugidas chars";
    }

    @Override
    protected List<FuzzingStrategy> fuzzStrategy() {
        return PayloadGenerator.getAbugidasChars().stream().map(value -> FuzzingStrategy.replace().withData(value)).toList();
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with abugidas chars in the targeted header";
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}