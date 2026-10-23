package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingConstraints;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.OpenApiUtils;

/**
 * Abstract base class for fuzzers expecting only 2xx responses for base fields.
 * Extends the {@link BaseFieldsFuzzer} class and provides a constructor
 * to initialize common dependencies for fuzzing base fields with the expectation of only 2xx responses.
 */
public abstract class ExpectOnly2XXBaseFieldsFuzzer extends BaseFieldsFuzzer {

    /**
     * Constructor for initializing common dependencies for fuzzing base fields with the expectation of only 2xx responses.
     *
     * @param sc The {@link ServiceCaller} used to make service calls.
     * @param lr The {@link TestCaseListener} for reporting test case events.
     * @param cp The {@link FilesArguments} for file-related arguments.
     */
    protected ExpectOnly2XXBaseFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.TWOXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.TWOXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamilyPredefined.TWOXX;
    }

    @Override
    protected ResponseCodeFamily getExpectedResponseCodeBasedOnConstraints(FuzzingData data, String fuzzedField, boolean isFuzzedValueMatchingPattern, FuzzingConstraints fuzzingConstraints) {
        if (data.getPath() != null && OpenApiUtils.getPathVariables(data.getPath()).contains(fuzzedField)) {
            return this.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern();
        }
        return super.getExpectedResponseCodeBasedOnConstraints(data, fuzzedField, isFuzzedValueMatchingPattern, fuzzingConstraints);
    }
}
