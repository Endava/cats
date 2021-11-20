package com.endava.cats.fuzzer.fields.leading;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.WhitespaceFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@FieldFuzzer
@WhitespaceFuzzer
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='validateAndTrim' and ${fuzzer.fields.LeadingWhitespacesInFieldsFuzzer.enabled}")
public class LeadingWhitespacesInFieldsValidateTrimFuzzer extends LeadingWhitespacesInFieldsTrimValidateFuzzer {

    @Autowired
    protected LeadingWhitespacesInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }
}