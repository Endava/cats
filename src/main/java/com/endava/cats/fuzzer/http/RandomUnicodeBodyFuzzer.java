package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends random unicode characters as body.
 */
@Singleton
@HttpFuzzer
public class RandomUnicodeBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
    /**
     * Creates a new RandomUnicodeBodyFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public RandomUnicodeBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return String.join("", UnicodeGenerator.getControlCharsFields());
    }

    @Override
    protected String getScenario() {
        return "Send a request with an random unicode string body";
    }

    @Override
    public String description() {
        return "send a request with a random unicode string body";
    }
}
