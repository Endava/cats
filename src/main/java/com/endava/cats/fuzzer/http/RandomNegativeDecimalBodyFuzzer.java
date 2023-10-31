package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@HttpFuzzer
public class RandomNegativeDecimalBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
    @Inject
    public RandomNegativeDecimalBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getPayload(FuzzingData data) {
        double value = Double.parseDouble(NumberGenerator.generateVeryLargeDecimal(3));
        return String.valueOf(-value);
    }

    @Override
    protected String getScenario() {
        return "Send a request with an random negative decimal body";
    }

    @Override
    public String description() {
        return "send a request with a random negative decimal body";
    }
}
