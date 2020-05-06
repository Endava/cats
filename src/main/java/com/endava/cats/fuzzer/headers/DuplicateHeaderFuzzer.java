package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class DuplicateHeaderFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateHeaderFuzzer.class);
    private static final String CATS_FUZZY_HEADER = "Cats-Fuzzy-Header";

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    @Autowired
    public DuplicateHeaderFuzzer(ServiceCaller sc, TestCaseListener lr) {
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
        List<CatsHeader> headers = new ArrayList<>(data.getHeaders());
        CatsHeader header = CatsHeader.builder().name(CATS_FUZZY_HEADER).required(false).value(CATS_FUZZY_HEADER).build();

        if (headers.isEmpty()) {
            headers.add(header);
            headers.add(header.copy());
        } else {
            header = headers.get(0);
            headers.add(header.copy());
        }

        testCaseListener.addScenario(LOGGER, "Scenario: add a duplicate header inside the request: name [{}], value [{}]. All other details are similar to a happy flow", header.getName(), header.getTruncatedValue());
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should get a 4XX response code");

        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath()).headers(headers).payload(data.getPayload()).build());

        testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.FOURXX);
    }


    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "send a 'happy' flow request and duplicate an existing header";
    }
}