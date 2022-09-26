package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.SpacesCharsBaseFuzzer;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.PayloadUtils;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@HeaderFuzzer
public class LeadingSpacesInHeadersFuzzer extends SpacesCharsBaseFuzzer {

    protected LeadingSpacesInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "trail value with spaces";
    }

    @Override
    public List<String> getInvisibleChars() {
        List<String> leadingChars = new ArrayList<>(PayloadUtils.getSpacesHeaders());
        leadingChars.remove("\r");
        return leadingChars;
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }
}
