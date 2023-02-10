package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.strategy.FuzzingStrategy;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
public class CRLFHeadersFuzzer extends InvisibleCharsBaseFuzzer {
    protected CRLFHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "send CR & LF characters";
    }

    @Override
    public List<String> getInvisibleChars() {
        return List.of("\r\n");
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
