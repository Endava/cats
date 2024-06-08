package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.ValidateAndSanitize;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends zero-width characters in fields for the validate then sanitize strategy.
 */
@FieldFuzzer
@Singleton
@ValidateAndSanitize
public class ZeroWidthCharsInValuesFieldsValidateSanitizeFuzzer extends ZeroWidthCharsInValuesFieldsSanitizeValidateFuzzer {
    /**
     * Constructor for initializing common dependencies for fuzzing base fields with the expectation of only 4xx responses.
     *
     * @param sc The {@link ServiceCaller} used to make service calls.
     * @param lr The {@link TestCaseListener} for reporting test case events.
     * @param cp The {@link FilesArguments} for file-related arguments.
     */
    protected ZeroWidthCharsInValuesFieldsValidateSanitizeFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }
}
