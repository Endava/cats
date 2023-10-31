package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@HttpFuzzer
public class ZeroDecimalBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
    @Inject
    public ZeroDecimalBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return "0.0";
    }

    @Override
    protected String getScenario() {
        return "Send a request with decimal 0.0 as body";
    }

    @Override
    public String description() {
        return "send a request with decimal 0.0 as body";
    }
}
