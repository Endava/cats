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
 * Fuzzer that sends a compact set of high-impact control characters in string fields
 * for services following the validate-then-sanitize strategy.
 */
@FieldFuzzer
@Singleton
@ValidateAndSanitize
public class ControlCharsInStringFieldsValidateSanitizeFuzzer extends ControlCharsInStringFieldsSanitizeValidateFuzzer {

    /**
     * Creates a new ControlCharsInStringFieldsValidateSanitizeFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    protected ControlCharsInStringFieldsValidateSanitizeFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
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
