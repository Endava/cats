package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fuzzer that will send a null request.
 */
@Singleton
@HttpFuzzer
public class NullBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {

    /**
     * Creates a new NullBodyFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public NullBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return "null";
    }

    @Override
    protected String getScenario() {
        return "Send a request with a NULL body";
    }

    @Override
    public String description() {
        return "send a request with a NULL body";
    }
}