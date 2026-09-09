package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.MutationTarget;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
        String payload = RequiredOnlyPayloadGenerator.generate(data.getPayload(), data.getAllRequiredFields());

        simpleExecutor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .fuzzer(this)
                .payload(payload)
                .mutationTarget(HttpMethod.requiresBody(data.getMethod())
                        ? MutationTarget.requestBody()
                        : MutationTarget.query("Optional parameters"))
                .scenario("Send a request containing only required fields")
                .logger(logger)
                .build());
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
