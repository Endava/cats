package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fuzzer that send am empty json body.
 */
@Singleton
@HttpFuzzer
public class EmptyJsonBodyFuzzer extends BaseHttpWithPayloadSimpleFuzzer {

    /**
     * Creates a new EmptyJsonBodyFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public EmptyJsonBodyFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected String getScenario() {
        return "Send a request with an empty json body";
    }

    @Override
    protected String getPayload(FuzzingData data) {
        return "{}";
    }

    @Override
    protected ResponseCodeFamily getExpectedResponseCode(FuzzingData data) {
        if (doesNotHaveRequiredFieldsInRoot(data)) {
            return ResponseCodeFamilyPredefined.TWOXX;
        }
        return super.getExpectedResponseCode(data);
    }

    @Override
    public String description() {
        return "send a request with a empty json body";
    }


    private boolean doesNotHaveRequiredFieldsInRoot(FuzzingData data) {
        return data.getAllRequiredFields().stream().allMatch(field -> field.contains("#"));
    }
}