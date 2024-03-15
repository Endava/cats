package com.endava.cats.fuzzer.fields.trailing;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

/**
 * Fuzzer that trails valid field values with multi code point emojis.
 */
@Singleton
@FieldFuzzer
@EmojiFuzzer
@ValidateAndTrim
public class TrailingMultiCodePointEmojisInFieldsValidateTrimFuzzer extends TrailingMultiCodePointEmojisInFieldsTrimValidateFuzzer {

    /**
     * Creates a new TrailingMultiCodePointEmojisInFieldsValidateTrimFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    protected TrailingMultiCodePointEmojisInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }
}