package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.WhitespaceFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.generator.simple.UnicodeGenerator;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@WhitespaceFuzzer
public class TrailingWhitespacesInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public TrailingWhitespacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "trail values with unicode separators";
    }

    @Override
    public List<String> getInvisibleChars() {
        return UnicodeGenerator.getSeparatorsHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.trail();
    }
}
