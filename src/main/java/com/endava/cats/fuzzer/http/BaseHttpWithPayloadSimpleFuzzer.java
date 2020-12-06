package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseHttpWithPayloadSimpleFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;

    @Autowired
    protected BaseHttpWithPayloadSimpleFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil catsUtil) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = catsUtil;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(logger, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        testCaseListener.addScenario(logger, this.getScenario());
        testCaseListener.addExpectedResult(logger, "Expected result: should get a 4XX response code");

        ServiceData serviceData = ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(this.getPayload(data)).replaceRefData(false).build();

        if (catsUtil.isHttpMethodWithPayload(data.getMethod())) {
            CatsResponse response = serviceCaller.call(data.getMethod(), serviceData);
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamily.FOURXX);
        } else {
            testCaseListener.skipTest(logger, "Method " + data.getMethod() + " not supported by " + this.toString());
        }
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    protected abstract String getScenario();

    protected abstract String getPayload(FuzzingData data);
}
