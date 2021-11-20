package com.endava.cats.fuzzer.fields.trailing;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.ControlCharFuzzer;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@FieldFuzzer
@ControlCharFuzzer
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='validateAndTrim' and ${fuzzer.fields.TrailingControlCharsInFieldsFuzzer.enabled}")
public class TrailingControlCharsInFieldsValidateTrimFuzzer extends TrailingControlCharsInFieldsTrimValidateFuzzer {

    @Autowired
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