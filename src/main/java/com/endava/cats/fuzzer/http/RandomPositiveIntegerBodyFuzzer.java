package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends a random positive integer as body.
 */
@Singleton
@HttpFuzzer
public class RandomPositiveIntegerBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
    /**
     * Creates a new RandomPositiveIntegerBodyFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public RandomPositiveIntegerBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return NumberGenerator.generateVeryLargeInteger(3);
    }

    @Override
    protected String getScenario() {
        return "Send a request with a random positive integer body";
    }

    @Override
    public String description() {
        return "send a request with a random positive integer body";
    }
}
