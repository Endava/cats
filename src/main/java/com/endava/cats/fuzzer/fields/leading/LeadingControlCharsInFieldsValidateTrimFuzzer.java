package com.endava.cats.fuzzer.fields.leading;

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
 * Fuzzer prefixing valid field values with control chars.
 */
@Singleton
@FieldFuzzer
@ControlCharFuzzer
@ValidateAndTrim
public class LeadingControlCharsInFieldsValidateTrimFuzzer extends LeadingControlCharsInFieldsTrimValidateFuzzer {

    /**
     * Creates a new LeadingControlCharsInFieldsValidateTrimFuzzer instance.
     *
     * @param sc The {@link ServiceCaller} used to make service calls
     * @param lr The {@link TestCaseListener} for reporting test case events
     * @param cu The {@link CatsUtil} for utility functions related to CATS
     * @param cp The {@link FilesArguments} for file-related arguments
     */
    protected LeadingControlCharsInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }
}