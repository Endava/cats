package com.endava.cats.fuzzer.executor;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.FuzzingResult;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

    /**
     * Constructs a new instance of FieldsIteratorExecutor.
     *
     * <p>This executor is designed to iterate over fields, leveraging the provided ServiceCaller to make service calls,
     * TestCaseListener for handling test case events, CatsUtil for working with Cats DSL and configurations,
     * MatchArguments for specifying criteria for matching fields, and FilesArguments for handling files-related parameters.</p>
     *
     * @param serviceCaller    The ServiceCaller instance responsible for making service calls during field iteration.
     * @param testCaseListener The TestCaseListener instance responsible for handling test case events.
     * @param catsUtil         The CatsUtil instance providing utility methods for working with Cats DSL and configurations.
     * @param ma               The MatchArguments providing criteria for matching fields during iteration.
     * @param fa               The FilesArguments containing parameters related to working with files during iteration.
     */
    @Inject
    public FieldsIteratorExecutor(ServiceCaller serviceCaller, TestCaseListener testCaseListener, CatsUtil catsUtil, MatchArguments ma, FilesArguments fa) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.catsUtil = catsUtil;
        this.matchArguments = ma;
        this.filesArguments = fa;
    }

    /**
     * Executes field iteration based on the provided FieldsIteratorExecutorContext.
     *
     * <p>This method processes field iteration by obtaining all fields associated with the HTTP method from the provided
     * FuzzingData. It logs information about all available fields and identifies fields marked for removal based on
     * reference data. The identified fields are then excluded from fuzzing, and additional processing is performed.</p>
     *
     * @param context The FieldsIteratorExecutorContext containing information for field iteration execution,
     *                including FuzzingData, logger, and other relevant parameters.
     */
    public void execute(FieldsIteratorExecutorContext context) {
        Set<String> allFields = context.getFuzzingData().getAllFieldsByHttpMethod();
        context.getLogger().debug("All fields: {}", allFields);
        List<String> fieldsToBeRemoved = filesArguments.getRefData(context.getFuzzingData().getPath()).entrySet()
                .stream().filter(entry -> String.valueOf(entry.getValue()).equalsIgnoreCase(CATS_REMOVE_FIELD)).map(Map.Entry::getKey).toList();
        context.getLogger().note("The following fields marked as [{}] in refData will not be fuzzed: {}", CATS_REMOVE_FIELD, fieldsToBeRemoved);

        fieldsToBeRemoved.forEach(allFields::remove);

        for (String fuzzedField : allFields) {
            Schema<?> fuzzedFieldSchema = context.getFuzzingData().getRequestPropertyTypes().get(fuzzedField);
            if (context.getSchemaFilter().test(fuzzedFieldSchema) && context.getFieldFilter().test(fuzzedField)) {
                for (String currentValue : context.getFuzzValueProducer().apply(fuzzedFieldSchema, fuzzedField)) {
                    testCaseListener.createAndExecuteTest(context.getLogger(), context.getFuzzer(), () -> executeTestCase(context, fuzzedField, currentValue));
                }
            } else {
                context.getLogger().debug("Skipping [{}]. " + context.getSkipMessage(), fuzzedField);
            }
        }
    }

    private void executeTestCase(FieldsIteratorExecutorContext context, String fuzzedField, String currentValue) {
        FuzzingStrategy strategy = context.getFuzzingStrategy().withData(currentValue);
        context.getLogger().debug("Applying [{}] for field [{}]", strategy, fuzzedField);

        testCaseListener.addScenario(context.getLogger(), context.getScenario() + "  Current field [{}] [{}]", fuzzedField, strategy);
        testCaseListener.addExpectedResult(context.getLogger(), "Should return [{}]",
                context.getExpectedResponseCode() != null ? context.getExpectedResponseCode().asString() : "a response that doesn't match" + matchArguments.getMatchString());

        FuzzingResult fuzzingResult = this.getFuzzingResult(context, fuzzedField, strategy);

        CatsResponse response = serviceCaller.call(
                ServiceData.builder()
                        .relativePath(context.getFuzzingData().getPath())
                        .contractPath(context.getFuzzingData().getContractPath())
                        .headers(context.getFuzzingData().getHeaders())
                        .payload(fuzzingResult.json())
                        .queryParams(context.getFuzzingData().getQueryParams())
                        .httpMethod(context.getFuzzingData().getMethod())
                        .contentType(context.getFuzzingData().getFirstRequestContentType())
                        .replaceRefData(context.isReplaceRefData())
                        .build());

        if (context.getExpectedResponseCode() != null) {
            testCaseListener.reportResult(context.getLogger(), context.getFuzzingData(), response, context.getExpectedResponseCode());
        } else if (!matchArguments.isAnyMatchArgumentSupplied() || matchArguments.isMatchResponse(response) || matchArguments.isInputReflected(response, currentValue)) {
            testCaseListener.reportResultError(context.getLogger(), context.getFuzzingData(), "Response matches arguments", "Response matches" + matchArguments.getMatchString());
        } else {
            testCaseListener.skipTest(context.getLogger(), "Skipping test as response does not match given matchers!");
        }
    }

    private FuzzingResult getFuzzingResult(FieldsIteratorExecutorContext context, String fuzzedField, FuzzingStrategy strategy) {
        if (context.isSimpleReplaceField()) {
            return catsUtil.justReplaceField(context.getFuzzingData().getPayload(), fuzzedField, strategy.getData());
        }
        return catsUtil.replaceField(context.getFuzzingData().getPayload(), fuzzedField, strategy);
    }

    /**
     * Checks if a given field is not a discriminator based on information provided by the associated TestCaseListener.
     *
     * <p>This method delegates the determination of whether a field is not a discriminator to the TestCaseListener.
     * It returns true if the associated TestCaseListener confirms that the specified field is not a discriminator;
     * otherwise, it returns false.</p>
     *
     * @param field The field to check for being not a discriminator.
     * @return {@code true} if the field is confirmed not to be a discriminator, {@code false} otherwise.
     */
    public boolean isFieldNotADiscriminator(String field) {
        return this.testCaseListener.isFieldNotADiscriminator(field);
    }
}