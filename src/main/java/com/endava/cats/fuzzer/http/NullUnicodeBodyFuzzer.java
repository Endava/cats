package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends the null unicode value as body.
 */
@Singleton
@HttpFuzzer
public class NullUnicodeBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {

    /**
     * Creates a new NullUnicodeBodyFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public NullUnicodeBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return "\u0000";
    }

    @Override
    protected String getScenario() {
        return "Send a request with a \\u0000 body";
    }

    @Override
    public String description() {
        return "send a request with a \\u0000 body";
    }
}