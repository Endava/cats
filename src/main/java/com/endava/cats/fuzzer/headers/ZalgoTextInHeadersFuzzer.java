package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.PayloadUtils;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

@HeaderFuzzer
@Singleton
public class ZalgoTextInHeadersFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {

    public ZalgoTextInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "zalgo text";
    }

    @Override
    protected List<FuzzingStrategy> fuzzStrategy() {
        return Collections.singletonList(FuzzingStrategy.replace().withData(PayloadUtils.getZalgoText()));
    }

    @Override
    public String description() {
        return "iterate through each header and send zalgo text in the targeted header";
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}