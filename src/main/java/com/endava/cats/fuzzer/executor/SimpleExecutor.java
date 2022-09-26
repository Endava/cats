package com.endava.cats.fuzzer.executor;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseListener;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SimpleExecutor {
    private final TestCaseListener testCaseListener;
    private final ServiceCaller serviceCaller;

    @Inject
    public SimpleExecutor(TestCaseListener testCaseListener, ServiceCaller serviceCaller) {
        this.testCaseListener = testCaseListener;
        this.serviceCaller = serviceCaller;
    }

    public void execute(SimpleExecutorContext context) {
        testCaseListener.createAndExecuteTest(context.getLogger(), context.getFuzzer(), () -> {
            testCaseListener.addScenario(context.getLogger(), context.getScenario());
            testCaseListener.addExpectedResult(context.getLogger(), "Should return {}", context.getExpectedSpecificResponseCode());
            CatsResponse response = serviceCaller.call(
                    ServiceData.builder()
                            .relativePath(context.getFuzzingData().getPath())
                            .headers(context.getHeaders())
                            .payload(context.getPayload())
                            .queryParams(context.getFuzzingData().getQueryParams())
                            .httpMethod(context.getFuzzingData().getMethod())
                            .contentType(context.getFuzzingData().getFirstRequestContentType())
                            .replaceRefData(context.isReplaceRefData())
                            .skippedHeaders(context.getSkippedHeaders())
                            .build());

            if (context.getRunFilter().test(context.getFuzzingData().getMethod())) {
                testCaseListener.reportResult(context.getLogger(), context.getFuzzingData(), response, context.getExpectedResponseCode());
            } else {
                testCaseListener.skipTest(context.getLogger(), "Method %s not supported by %s".formatted(context.getFuzzingData().getMethod(), context.getFuzzer()));
            }
        });
    }
}
