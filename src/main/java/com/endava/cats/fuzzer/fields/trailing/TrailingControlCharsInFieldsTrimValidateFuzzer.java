package com.endava.cats.fuzzer.fields.trailing;

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
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}' == 'trimAndValidate' and ${fuzzer.fields.TrailingControlCharsInFieldsFuzzer.enabled}")
public class TrailingControlCharsInFieldsTrimValidateFuzzer extends TrailingInvisibleCharsTrimValidateFuzzer {

    @Autowired
    protected TrailingControlCharsInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.CONTROL_CHARS_FIELDS;
    }

    @Override
    String getInvisibleCharDescription() {
        return "unicode control chars";
    }
}