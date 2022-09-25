package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.CatsHttpExecutor;
import com.endava.cats.model.FuzzingData;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@HttpFuzzer
public class DummyRequestFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
    protected static final String DUMMY_JSON = "{\"cats\":\"cats\"}";

    @Inject
    public DummyRequestFuzzer(CatsHttpExecutor executor) {
        super(executor);
    }

    @Override
    protected String getScenario() {
        return "Send a dummy JSON";
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return DUMMY_JSON;
    }

    @Override
    public String description() {
        return "send a dummy json request {'cats': 'cats'}";
    }
}
