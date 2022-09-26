package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.SpacesCharsBaseFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.PayloadUtils;

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
        return PayloadUtils.getSpacesHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }
}
