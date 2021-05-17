package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fuzzer that sends a "happy" flow request with no fuzzing applied
 */
@Component
@HttpFuzzer
@ConditionalOnProperty(value = "fuzzer.http.HappyFuzzer.enabled", havingValue = "true")
public class HappyFuzzer implements Fuzzer {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(HappyFuzzer.class);

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    @Autowired
    public HappyFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        testCaseListener.addScenario(LOGGER, "Send a 'happy' flow request will all fields and all headers in");
        testCaseListener.addExpectedResult(LOGGER, "Should get a 2XX response code");
        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(data.getPayload()).queryParams(data.getQueryParams()).httpMethod(data.getMethod()).build());

        testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.TWOXX);
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "send a request with all fields and headers populated";
    }
}
