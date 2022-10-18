package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.strategy.FuzzingStrategy;

import java.util.List;

/**
 * Base class for fuzzers sending Control Chars or Unicode Separators in headers.
 */
public abstract class InvisibleCharsBaseFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {

    protected InvisibleCharsBaseFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public List<FuzzingStrategy> fuzzStrategy() {
        return this.getInvisibleChars()
                .stream().map(value -> concreteFuzzStrategy().withData(value)).toList();
    }

    @Override
    public String description() {
        return "iterate through each header and " + typeOfDataSentToTheService();
    }

    public abstract List<String> getInvisibleChars();

    public abstract FuzzingStrategy concreteFuzzStrategy();
}