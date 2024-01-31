package com.endava.cats.fuzzer.fields.trailing;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.ValidateAndTrim;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

/**
 * Fuzzer that trails valid field values with control chars.
 */
@Singleton
@FieldFuzzer
@ControlCharFuzzer
@ValidateAndTrim
public class TrailingControlCharsInFieldsValidateTrimFuzzer extends TrailingControlCharsInFieldsTrimValidateFuzzer {

    /**
     * Creates a new TrailingControlCharsInFieldsValidateTrimFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cu utility class
     * @param cp files arguments
     */
    protected TrailingControlCharsInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }
}