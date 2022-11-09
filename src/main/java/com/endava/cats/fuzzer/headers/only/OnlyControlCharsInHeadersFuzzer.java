package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.generator.simple.UnicodeGenerator;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@ControlCharFuzzer
public class OnlyControlCharsInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public OnlyControlCharsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "replace value with control chars";
    }

    @Override
    public List<String> getInvisibleChars() {
        return UnicodeGenerator.getControlCharsHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}
