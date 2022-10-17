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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.endava.cats.io.ServiceCaller.CATS_REMOVE_FIELD;

/**
 * Executors are meant to be the units that will execute the fuzzer's payloads and facilitate the interaction with all the reporting.
 * <p>
 * <b>
 * They are not responsible for the creation of the payloads, but only for making sure the tests are executed within a testing context
 * and results are reported accordingly.
 * </b>
 * </p>
 * <p>
 * {@code FieldsIteratorExecutor} is an Executor that iterates through all fields and applies the supplied {@code FieldsIteratorExecutorContext#fuzzingStrategy}.
 * This is useful when Fuzzers want to fuzz each request field iteratively.
 * </p>
 * <p>
 * By default, the Executor will call {@code TestCaseListener#reportResult} matching against the supplied {@code FieldsIteratorExecutorContext#expectedResponseCode}.
 * You can also supply {@code FieldsIteratorExecutorContext#schemaFiler} and/or a  {@code FieldsIteratorExecutorContext#fieldFilter}if you want to skip reporting for specific schemas or fields.
 * If {@code matchArguments are supplied} and no {@code FieldsIteratorExecutorContext#expectedResponseCode} the Executor will match the arguments against the response.
 * </p>
 * <p>
 * You must also supply a  {@code FieldsIteratorExecutorContext#fuzzValueProducer} for the Executor that will be used as the source of fuzzing.
 * </p>
 */
@Singleton
public class FieldsIteratorExecutor {

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;
    private final MatchArguments matchArguments;
    private final FilesArguments filesArguments;

    @Inject
    public FieldsIteratorExecutor(ServiceCaller serviceCaller, TestCaseListener testCaseListener, CatsUtil catsUtil, MatchArguments ma, FilesArguments fa) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.catsUtil = catsUtil;
        this.matchArguments = ma;
        this.filesArguments = fa;
    }

    public void execute(FieldsIteratorExecutorContext context) {
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
                    testCaseListener.createAndExecuteTest(context.getLogger(), context.getFuzzer(), () -> executeTestCase(context, fuzzedField, currentValue));
                }
            } else {
                context.getLogger().skip("Skipping [{}]. " + context.getSkipMessage(), fuzzedField);
            }
        }
    }

    private void executeTestCase(FieldsIteratorExecutorContext context, String fuzzedField, String currentValue) {
        FuzzingStrategy strategy = context.getFuzzingStrategy().withData(currentValue);
        context.getLogger().debug("Applying [{}] for field [{}]", strategy, fuzzedField);

        testCaseListener.addScenario(context.getLogger(), context.getScenario() + "  Current field [{}] [{}]", fuzzedField, strategy);
        testCaseListener.addExpectedResult(context.getLogger(), "Should return [{}]",
                context.getExpectedResponseCode() != null ? context.getExpectedResponseCode().asString() : "a valid response");

        FuzzingResult fuzzingResult = catsUtil.replaceField(context.getFuzzingData().getPayload(), fuzzedField, strategy);

        CatsResponse response = serviceCaller.call(
                ServiceData.builder()
                        .relativePath(context.getFuzzingData().getPath())
                        .contractPath(context.getFuzzingData().getContractPath())
                        .headers(context.getFuzzingData().getHeaders())
                        .payload(fuzzingResult.getJson())
                        .queryParams(context.getFuzzingData().getQueryParams())
                        .httpMethod(context.getFuzzingData().getMethod())
                        .contentType(context.getFuzzingData().getFirstRequestContentType())
                        .replaceRefData(context.isReplaceRefData())
                        .build());

        if (context.getExpectedResponseCode() != null) {
            testCaseListener.reportResult(context.getLogger(), context.getFuzzingData(), response, context.getExpectedResponseCode());
        } else if (matchArguments.isMatchResponse(response) || !matchArguments.isAnyMatchArgumentSupplied()) {
            testCaseListener.reportResultError(context.getLogger(), context.getFuzzingData(), "Check response details", "Service call completed. Please check response details");
        } else {
            testCaseListener.skipTest(context.getLogger(), "Skipping test as response does not match given matchers!");
        }
    }

    public boolean isFieldNotADiscriminator(String field) {
        return this.testCaseListener.isFieldNotADiscriminator(field);
    }
}