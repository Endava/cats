package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@HttpFuzzer
public class MalformedJsonFuzzer extends BaseHttpWithPayloadSimpleFuzzer {

    @Inject
    public MalformedJsonFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    protected String getScenario() {
        return "Send a malformed JSON which has the string 'bla' at the end (the report won't actually display it as all requests need to be actual JSONs, but please check the logs to see the actual request payload)";
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return data.getPayload() + "bla";
    }

    @Override
    public String description() {
        return "send a malformed json request which has the String 'bla' at the end";
    }
}
