package com.endava.cats.fuzzer.executor;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Getter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.endava.cats.io.ServiceCaller.CATS_REMOVE_FIELD;

@Singleton
public class CatsFieldsExecutor {

    private final ServiceCaller serviceCaller;
    @Getter
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;

    private final MatchArguments matchArguments;

    private final FilesArguments filesArguments;

    @Inject
    public CatsFieldsExecutor(ServiceCaller serviceCaller, TestCaseListener testCaseListener, CatsUtil catsUtil, MatchArguments ma, FilesArguments fa) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.catsUtil = catsUtil;
        this.matchArguments = ma;
        this.filesArguments = fa;
    }

    public void execute(CatsFieldsExecutorContext context) {
        Set<String> allFields = context.getFuzzingData().getAllFieldsByHttpMethod();
        context.getLogger().debug("All fields: {}", allFields);
        List<String> fieldsToBeRemoved = filesArguments.getRefData(context.getFuzzingData().getPath()).entrySet()
                .stream().filter(entry -> entry.getValue().equalsIgnoreCase(CATS_REMOVE_FIELD)).map(Map.Entry::getKey).toList();
        context.getLogger().note("The following fields marked as [{}] in refData will not be fuzzed: {}", CATS_REMOVE_FIELD, fieldsToBeRemoved);

        fieldsToBeRemoved.forEach(allFields::remove);

        for (String fuzzedField : allFields) {
            Schema<?> fuzzedFieldSchema = context.getFuzzingData().getRequestPropertyTypes().get(fuzzedField);
            if (context.getSchemaFilter().test(fuzzedFieldSchema) && context.getFieldFilter().test(fuzzedField)) {
                for (String currentValue : context.getFuzzValueProducer().apply(fuzzedFieldSchema)) {
                    testCaseListener.createAndExecuteTest(context.getLogger(), context.getFuzzer(), () -> {
                                FuzzingStrategy strategy = context.getFuzzingStrategy().withData(currentValue);
                                context.getLogger().debug("Applying [{}] for field [{}]", strategy, fuzzedField);

                                testCaseListener.addScenario(context.getLogger(), context.getScenario() + "  Current field [{}] [{}]", fuzzedField, strategy);
                                testCaseListener.addExpectedResult(context.getLogger(), "Should return [{}]",
                                        context.getExpectedResponseCode() != null ? context.getExpectedResponseCode().asString() : "a valid response");

                                FuzzingResult fuzzingResult = catsUtil.replaceField(context.getFuzzingData().getPayload(), fuzzedField, strategy);

                                CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(context.getFuzzingData().getPath()).headers(context.getFuzzingData().getHeaders())
                                        .payload(fuzzingResult.getJson()).queryParams(context.getFuzzingData().getQueryParams()).httpMethod(context.getFuzzingData().getMethod())
                                        .contentType(context.getFuzzingData().getFirstRequestContentType()).build());

                                if (context.getExpectedResponseCode() != null) {
                                    testCaseListener.reportResult(context.getLogger(), context.getFuzzingData(), response, context.getExpectedResponseCode());
                                } else if (matchArguments.isMatchResponse(response) || !matchArguments.isAnyMatchArgumentSupplied()) {
                                    testCaseListener.reportError(context.getLogger(), "Service call completed. Please check response details");
                                } else {
                                    testCaseListener.skipTest(context.getLogger(), "Skipping test as response does not match given matchers!");
                                }
                            }
                    );
                }
            } else {
                context.getLogger().skip("Skipping [{}]. " + context.getSkipMessage(), fuzzedField);
            }
        }
    }
}