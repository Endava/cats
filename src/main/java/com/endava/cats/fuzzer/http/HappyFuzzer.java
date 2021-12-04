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

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Fuzzer that sends a "happy" flow request with no fuzzing applied.
 */
@Singleton
@HttpFuzzer
public class HappyFuzzer implements Fuzzer {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(HappyFuzzer.class);

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    @Inject
    public HappyFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        testCaseListener.addScenario(LOGGER, "Send a 'happy' flow request with all fields and all headers in");
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
