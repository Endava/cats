package com.endava.cats.fuzzer.executor;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Executors are meant to be the units that will execute the fuzzer's payloads and facilitate the interaction with all the reporting.
 * <p>
 * <b>
 * They are not responsible for the creation of the payloads, but only for making sure the tests are executed within a testing context
 * and results are reported accordingly.
 * </b>
 * </p>
 * <p>
 * {@code SimpleExecutor} is the simplest form of executors. It will take the data from the Fuzzer and execute it, without any additional logic.
 * This is useful when Fuzzers only create one interaction with the service.
 * </p>
 * <p>
 * You can control how reporting is done using your own {@code SimpleExecutorContext#responseProcessor}. By default, the Executor will call
 * {@code TestCaseListener#reportResult} matching against the supplied {@code SimpleExecutorContext#expectedResponseCode}. You can also supply
 * a {@code SimpleExecutorContext#runFilter} if you want to skip reporting for specific HTTP methods.
 * </p>
 */
@ApplicationScoped
public class SimpleExecutor {
    private final TestCaseListener testCaseListener;
    private final ServiceCaller serviceCaller;

    /**
     * Constructs a new instance of SimpleExecutor.
     *
     * <p>This executor is designed to simplify the execution of test cases by relying on a provided
     * TestCaseListener for handling events and a ServiceCaller for making service calls during execution.</p>
     *
     * @param testCaseListener The TestCaseListener instance responsible for handling test case events.
     * @param serviceCaller    The ServiceCaller instance responsible for making service calls during execution.
     */
    @Inject
    public SimpleExecutor(TestCaseListener testCaseListener, ServiceCaller serviceCaller) {
        this.testCaseListener = testCaseListener;
        this.serviceCaller = serviceCaller;
    }

    /**
     * Executes the logic considering the given context.
     * This method will do the actual HTTP call to the service and match the response against expected behaviour.
     *
     * @param context the executor context
     */
    public void execute(SimpleExecutorContext context) {
        testCaseListener.createAndExecuteTest(context.getLogger(), context.getFuzzer(), () -> {
            testCaseListener.addScenario(context.getLogger(), context.getScenario());
            testCaseListener.addExpectedResult(context.getLogger(), "Should return {}" + context.getExpectedResult(), context.getExpectedSpecificResponseCode());

            if (!testCaseListener.shouldContinueExecution(context.getLogger(), context.getExpectedResponseCode())) {
                testCaseListener.skipTest(context.getLogger(), "Test skipped due to response code filtering");
                return;
            }

            testCaseListener.addExpectedResponse(context.getExpectedResponseForReporting());

            CatsResponse response = serviceCaller.call(toServiceData(context));

            if (testCaseListener.reportRateLimitedResponse(context.getLogger(), context.getFuzzingData(), response)) {
                return;
            }
            if (context.getResponseProcessor() != null) {
                context.getResponseProcessor().accept(response, context.getFuzzingData());
            } else {
                reportDefaultResult(context.getLogger(), context.getFuzzingData(), response, context.getExpectedResponseCode(), context.isMatchResponseResult(), context.isMatchResponseContentType());
            }
        }, context.getFuzzingData());
    }

    /**
     * Re-sends the request described by the given context from within the currently running test case,
     * without creating a new test case and without replacing the exchange already recorded for it.
     * This is meant to confirm that a finding reproduces.
     *
     * @param context the executor context describing the request to replay
     * @return the response received for the replayed request
     */
    public CatsResponse replay(SimpleExecutorContext context) {
        return testCaseListener.callPreservingCurrentExchange(() -> serviceCaller.call(toServiceData(context)));
    }

    /**
     * Sends the unfuzzed request of the given operation from within the currently running test case,
     * without creating a new test case and without replacing the exchange already recorded for it.
     * This is meant to obtain a fresh baseline response to compare a finding against.
     *
     * @param data the fuzzing data of the operation
     * @return the response received for the unfuzzed request
     */
    public CatsResponse replayOriginal(FuzzingData data) {
        return testCaseListener.callPreservingCurrentExchange(() -> serviceCaller.call(ServiceData.from(data).build()));
    }

    /**
     * Resolves explicit reference data into the baseline payload for fuzzers that subsequently construct
     * non-standard or raw JSON payloads and must bypass post-mutation reference data replacement.
     *
     * @param data the fuzzing data containing the generated payload
     * @return the payload with reference data applied, or the original payload if it is not valid JSON
     */
    public String resolvePayloadWithRefData(FuzzingData data, PrettyLogger logger, ResponseCodeFamily expectedResponseCode) {
        if (data == null || !isStructuredJson(data.getPayload())
                || !testCaseListener.shouldContinueExecution(logger, expectedResponseCode)) {
            return data == null ? null : data.getPayload();
        }
        return Optional.ofNullable(serviceCaller.replacePayloadWithRefData(ServiceData.from(data).build()))
                .orElse(data.getPayload());
    }

    private static boolean isStructuredJson(String payload) {
        if (!JsonUtils.isValidJson(payload)) {
            return false;
        }
        JsonElement parsed = JsonUtils.parseAsJsonElement(payload);
        return parsed != null && (parsed.isJsonObject() || parsed.isJsonArray());
    }

    private static ServiceData toServiceData(SimpleExecutorContext context) {
        return ServiceData.from(context.getFuzzingData())
                .relativePath(context.getPath())
                .headers(context.getHeaders())
                .payload(context.getPayload())
                .httpMethod(context.getHttpMethod())
                .replaceRefData(context.isReplaceRefData())
                .skippedHeaders(context.getSkippedHeaders())
                .addUserHeaders(context.isAddUserHeaders())
                .replaceUrlParams(context.isReplaceUrlParams())
                .validJson(context.isValidJson())
                .mutationTargets(context.getMutationTargets())
                .multipartMutation(context.getMultipartMutation())
                .build();
    }

    public void reportDefaultResult(PrettyLogger logger, FuzzingData data, CatsResponse response,
                                    ResponseCodeFamily expectedResponseCode, boolean matchResponseResult,
                                    boolean matchResponseContentType) {
        testCaseListener.reportResult(logger, data, response, expectedResponseCode, matchResponseResult, matchResponseContentType);
    }
}
