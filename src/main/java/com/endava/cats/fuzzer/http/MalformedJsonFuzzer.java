package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@HttpFuzzer
public class MalformedJsonFuzzer extends BaseHttpWithPayloadSimpleFuzzer {

    @Autowired
    public MalformedJsonFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil catsUtil) {
        super(sc, lr, catsUtil);
    }

    @Override
    protected String getScenario() {
        return "Scenario: Send a malformed JSON";
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
