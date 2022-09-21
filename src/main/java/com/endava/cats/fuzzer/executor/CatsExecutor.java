package com.endava.cats.fuzzer.executor;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingResult;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Getter;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CatsExecutor {

    private final ServiceCaller serviceCaller;
    @Getter
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;

    @Inject
    public CatsExecutor(ServiceCaller serviceCaller, TestCaseListener testCaseListener, CatsUtil catsUtil) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.catsUtil = catsUtil;
    }

    public void execute(CatsExecutorContext context) {
        for (String fuzzedField : context.getFuzzingData().getAllFieldsByHttpMethod()) {
            Schema<?> fuzzedFieldSchema = context.getFuzzingData().getRequestPropertyTypes().get(fuzzedField);

            if (context.getSchemaFilter().test(fuzzedFieldSchema) && context.getFieldFilter().test(fuzzedField)) {
                for (String currentValue : context.getFuzzValueProducer().apply(fuzzedFieldSchema)) {
                    testCaseListener.createAndExecuteTest(context.getLogger(), context.getFuzzer(), () -> {
                                testCaseListener.addScenario(context.getLogger(), context.getScenario() + "  Current field [{}]", fuzzedField);
                                testCaseListener.addExpectedResult(context.getLogger(), "Should return [{}]", context.getExpectedResponseCode().asString());
                                FuzzingResult fuzzingResult = catsUtil.replaceField(context.getFuzzingData().getPayload(), fuzzedField, context.getFuzzingStrategy().withData(currentValue));

                                context.getLogger().debug("Applying [{}] for field [{}]", context.getFuzzingStrategy(), fuzzedField);

                                CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(context.getFuzzingData().getPath()).headers(context.getFuzzingData().getHeaders())
                                        .payload(fuzzingResult.getJson()).queryParams(context.getFuzzingData().getQueryParams()).httpMethod(context.getFuzzingData().getMethod())
                                        .contentType(context.getFuzzingData().getFirstRequestContentType()).build());
                                testCaseListener.reportResult(context.getLogger(), context.getFuzzingData(), response, context.getExpectedResponseCode());
                            }
                    );
                }
            } else {
                context.getLogger().skip("Skipping [{}]. " + context.getSkipMessage(), fuzzedField);
            }
        }
    }
}