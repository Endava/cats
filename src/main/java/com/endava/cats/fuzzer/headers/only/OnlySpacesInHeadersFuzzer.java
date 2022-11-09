package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.SpacesCharsBaseFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.generator.simple.UnicodeGenerator;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
public class OnlySpacesInHeadersFuzzer extends SpacesCharsBaseFuzzer {

    protected OnlySpacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "replace value with spaces";
    }

    @Override
    public List<String> getInvisibleChars() {
        return UnicodeGenerator.getSpacesHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }
}
