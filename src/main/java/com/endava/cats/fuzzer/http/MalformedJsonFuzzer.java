package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@HttpFuzzer
public class MalformedJsonFuzzer extends BaseHttpWithPayloadSimpleFuzzer {

    @Inject
    public MalformedJsonFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getScenario() {
        return "Send a malformed JSON which has the string 'bla' at the end (the report won't actually display it as all requests need to be valid JSONs, but please check the logs to see the actual request payload)";
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return data.getPayload() + "bla";
    }

    @Override
    public String description() {
        return "send a malformed json request which has the String 'bla' at the end";
    }
}
