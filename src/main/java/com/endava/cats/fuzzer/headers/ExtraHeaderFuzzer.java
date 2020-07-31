package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class ExtraHeaderFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtraHeaderFuzzer.class);
    private static final String CATS_FUZZY_HEADER = "Cats-Fuzzy-Header";

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    @Autowired
    public ExtraHeaderFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getHeaders().isEmpty()) {
            LOGGER.info("No headers to fuzz");
        }
        testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        HashSet<CatsHeader> headerSet = new HashSet<>(data.getHeaders());
        headerSet.add(CatsHeader.builder().name(CATS_FUZZY_HEADER).required(false).value(CATS_FUZZY_HEADER).build());

        testCaseListener.addScenario(LOGGER, "Scenario: add extra header inside the request: name [{}], value [{}]. All other details are similar to a happy flow", CATS_FUZZY_HEADER, CATS_FUZZY_HEADER);
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should get a 2XX response code");

        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath())
                .headers(headerSet).payload(data.getPayload()).queryParams(data.getQueryParams()).build());

        testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.TWOXX);
    }


    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "send a 'happy' flow request and add an extra field inside the request called 'Cats-Fuzzy-Header'";
    }
}