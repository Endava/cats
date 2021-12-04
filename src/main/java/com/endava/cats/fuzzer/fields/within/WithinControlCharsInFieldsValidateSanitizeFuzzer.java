package com.endava.cats.fuzzer.fields.within;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.ControlCharFuzzer;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.ValidateAndSanitize;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;

@Singleton
@FieldFuzzer
@ControlCharFuzzer
@ValidateAndSanitize
public class WithinControlCharsInFieldsValidateSanitizeFuzzer extends WithinControlCharsInFieldsSanitizeValidateFuzzer {

    protected WithinControlCharsInFieldsValidateSanitizeFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }
}
