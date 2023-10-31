package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@HttpFuzzer
public class RandomPositiveIntegerBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
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
        return "Send a request with an random positive integer body";
    }

    @Override
    public String description() {
        return "send a request with a random positive integer body";
    }
}
