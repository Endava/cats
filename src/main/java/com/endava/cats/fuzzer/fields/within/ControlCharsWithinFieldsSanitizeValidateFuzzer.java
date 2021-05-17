package com.endava.cats.fuzzer.fields.within;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@FieldFuzzer
@ConditionalOnExpression(value = "'${sanitizationStrategy:sanitizeAndValidate}' == 'sanitizeAndValidate' and ${fuzzer.fields.ControlCharsWithinFieldsFuzzer.enabled}")
public class ControlCharsWithinFieldsSanitizeValidateFuzzer extends WithinInvisibleCharsSanitizeValidateFuzzer {

    @Autowired
    protected ControlCharsWithinFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.CONTROL_CHARS;
    }

    @Override
    String getInvisibleCharDescription() {
        return "unicode control characters";
    }
}
