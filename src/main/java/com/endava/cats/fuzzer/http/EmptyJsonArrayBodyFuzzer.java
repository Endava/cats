package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends an empty json array body.
 */
@Singleton
@HttpFuzzer
public class EmptyJsonArrayBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {

    /**
     * Creates a new EmptyJsonArrayBodyFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public EmptyJsonArrayBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getScenario() {
        return "Send a request with an empty json array body";
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return "[]";
    }

    @Override
    public String description() {
        return "send a request with a empty json array body";
    }
}