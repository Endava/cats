package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@HttpFuzzer
public class DummyRequestFuzzer extends BaseHttpWithPayloadSimpleFuzzer {
    protected static final String DUMMY_JSON = "{\"cats\":\"cats\"}";

    @Inject
    public DummyRequestFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil catsUtil) {
        super(sc, lr, catsUtil);
    }

    @Override
    protected String getScenario() {
        return "Send a dummy JSON";
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return DUMMY_JSON;
    }

    @Override
    public String description() {
        return "send a dummy json request {'cats': 'cats'}";
    }
}
