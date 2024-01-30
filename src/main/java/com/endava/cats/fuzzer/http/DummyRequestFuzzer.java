package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends a dummy request.
 */
@Singleton
@HttpFuzzer
public class DummyRequestFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
    static final String DUMMY_JSON = "{\"cats\":\"cats\"}";

    /**
     * Creates a new DummyRequestFuzzer instance
     *
     * @param executor the executor
     */
    @Inject
    public DummyRequestFuzzer(SimpleExecutor executor) {
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
