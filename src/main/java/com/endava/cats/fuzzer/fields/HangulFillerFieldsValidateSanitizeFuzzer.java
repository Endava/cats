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
 * Inserts Hangul Filler characters into field values.
 */
@FieldFuzzer
@Singleton
@ValidateAndSanitize
public class HangulFillerFieldsValidateSanitizeFuzzer extends HangulFillerFieldsSanitizeValidateFuzzer {
    /**
     * Creates a new HangulFillerFieldsValidateSanitizeFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    public HangulFillerFieldsValidateSanitizeFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }
}
