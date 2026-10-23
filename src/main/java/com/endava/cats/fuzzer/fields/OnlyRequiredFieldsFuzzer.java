package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Sends a valid request containing only fields marked as required in the OpenAPI contract.
 */
@Singleton
@FieldFuzzer
public class OnlyRequiredFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(OnlyRequiredFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new OnlyRequiredFieldsFuzzer instance.
     *
     * @param simpleExecutor the executor
     */
    @Inject
    public OnlyRequiredFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        boolean bodyMethod = HttpMethod.requiresBody(data.getMethod());
        String payload = RequiredOnlyPayloadGenerator.generate(
                data.getPayload(), data.getAllRequiredFields(), data.getPath(), bodyMethod);
        RequestTarget target = bodyMethod
                ? RequestTarget.requestBody()
                : RequestTarget.query("Optional parameters");

        boolean[] failed = {false};
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .fuzzer(this)
                .payload(payload)
                .mutationTarget(target)
                .scenario("Send a request containing only required fields")
                .responseProcessor((response, fuzzData) -> {
                    failed[0] = !ResponseCodeFamily.is2xxCode(response.getResponseCode());
                    simpleExecutor.reportDefaultResult(logger, fuzzData, response, ResponseCodeFamilyPredefined.TWOXX, true, true);
                })
                .logger(logger)
                .build());

        if (!failed[0]) {
            return;
        }
        List<String> fallbacks = RequiredOnlyPayloadGenerator.generateFallbackMinimalPayloads(
                data.getPayload(), data.getAllRequiredFields(), payload, data.getRequestPropertyTypes(), 12);
        for (String fallbackPayload : fallbacks) {
            if (!failed[0]) {
                break;
            }
            simpleExecutor.execute(SimpleExecutorContext.builder()
                    .fuzzingData(data)
                    .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                    .fuzzer(this)
                    .payload(fallbackPayload)
                    .mutationTarget(target)
                    .scenario("Send a request containing required fields and minimal optional fields")
                    .responseProcessor((response, fuzzData) -> {
                        failed[0] = !ResponseCodeFamily.is2xxCode(response.getResponseCode());
                        simpleExecutor.reportDefaultResult(logger, fuzzData, response, ResponseCodeFamilyPredefined.TWOXX, true, true);
                    })
                    .logger(logger)
                    .build());
        }
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send a request containing only required fields and expect a 2XX response";
    }
}
