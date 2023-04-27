package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.generator.simple.UnicodeGenerator;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@EmojiFuzzer
public class LeadingSingleCodePointEmojisInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public LeadingSingleCodePointEmojisInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public List<String> getInvisibleChars() {
        return UnicodeGenerator.getSingleCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "prefix values with single code point emojis";
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}
