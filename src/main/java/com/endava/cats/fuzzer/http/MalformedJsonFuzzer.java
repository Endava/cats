package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MalformedJsonFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MalformedJsonFuzzer.class);

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;

    @Autowired
    public MalformedJsonFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil catsUtil) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = catsUtil;
    }

    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(() ->
                process(data)
        );
    }

    private void process(FuzzingData data) {
        try {
            testCaseListener.addScenario(LOGGER, "Scenario: send a malformed JSON");
            testCaseListener.addExpectedResult(LOGGER, "Expected result: should get a 4XX response code");

            ServiceData serviceData = ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                    .payload(data.getPayload() + "bla").replaceRefData(false).build();

            if (catsUtil.isHttpMethodWithPayload(data.getMethod())) {
                CatsResponse response = serviceCaller.call(data.getMethod(), serviceData);
                testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.FOURXX);
            } else {
                testCaseListener.skipTest(LOGGER, "Method " + data.getMethod() + " not supported by " + this.toString());
            }

        } catch (Exception e) {
            testCaseListener.reportError(LOGGER, "Fuzzer [{}] failed due to [{}]", this.getClass().getSimpleName(), e);
        }
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "send a malformed json request which has the String 'bla' at the end";
    }
}
