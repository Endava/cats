package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fuzzer used to send negative integers as body.
 */
@Singleton
@HttpFuzzer
public class RandomNegativeIntegerBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
    /**
     * Creates a new RandomNegativeIntegerBodyFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public RandomNegativeIntegerBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getPayload(FuzzingData data) {
        long value = Long.parseLong(NumberGenerator.generateVeryLargeInteger(3));
        return String.valueOf(-value);
    }

    @Override
    protected String getScenario() {
        return "Send a request with an random negative integer body";
    }

    @Override
    public String description() {
        return "send a request with a random negative integer body";
    }
}
