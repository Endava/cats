package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

/**
 * This is a base class for Fuzzers that want to send invalid payloads for HTTP methods accepting bodies.
 * It expects a 4XX within the response.
 */
public abstract class BaseHttpWithPayloadSimpleFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    BaseHttpWithPayloadSimpleFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(logger, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        testCaseListener.addScenario(logger, this.getScenario());
        testCaseListener.addExpectedResult(logger, "Should get a 4XX response code");

        ServiceData serviceData = ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(this.getPayload(data)).replaceRefData(false).httpMethod(data.getMethod()).build();

        if (JsonUtils.isHttpMethodWithPayload(data.getMethod())) {
            CatsResponse response = serviceCaller.call(serviceData);
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX);
        } else {
            testCaseListener.skipTest(logger, "Method " + data.getMethod() + " not supported by " + this);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    /**
     * Returns the scenario to be displayed in the test report.
     *
     * @return the test scenario
     */
    protected abstract String getScenario();

    /**
     * Returns the payload to be sent to the service.
     *
     * @param data the current FuzzingData
     * @return the payload to be sent to the service
     */
    protected abstract String getPayload(FuzzingData data);
}
