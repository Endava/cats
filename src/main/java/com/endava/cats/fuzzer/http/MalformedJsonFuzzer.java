package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@HttpFuzzer
@ConditionalOnProperty(value = "fuzzer.http.MalformedJsonFuzzer.enabled", havingValue = "true")
public class MalformedJsonFuzzer extends BaseHttpWithPayloadSimpleFuzzer {

    @Autowired
    public MalformedJsonFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil catsUtil) {
        super(sc, lr, catsUtil);
    }

    @Override
    protected String getScenario() {
        return "Scenario: Send a malformed JSON which has the string 'bla' at the end (the report won't actually display it as all requests need to be actual JSONs, but please check the logs to see the actual request payload)";
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
