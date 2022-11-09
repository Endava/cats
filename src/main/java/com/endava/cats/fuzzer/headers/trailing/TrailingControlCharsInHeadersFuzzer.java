package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.generator.simple.UnicodeGenerator;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@HeaderFuzzer
@ControlCharFuzzer
public class TrailingControlCharsInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public TrailingControlCharsInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "trail values with control chars";
    }

    @Override
    public List<String> getInvisibleChars() {
        List<String> controlChars = new ArrayList<>(UnicodeGenerator.getControlCharsHeaders());
        controlChars.remove("\r");

        return controlChars;
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.trail();
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}