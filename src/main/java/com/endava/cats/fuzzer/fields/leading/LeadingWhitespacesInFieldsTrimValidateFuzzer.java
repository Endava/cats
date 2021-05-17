package com.endava.cats.fuzzer.fields.leading;


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
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='trimAndValidate' and ${fuzzer.fields.LeadingWhitespacesInFieldsFuzzer.enabled}")
public class LeadingWhitespacesInFieldsTrimValidateFuzzer extends LeadingInvisibleCharsTrimValidateFuzzer {


    @Autowired
    protected LeadingWhitespacesInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.WHITESPACES_FIELDS;
    }

    @Override
    String getInvisibleCharDescription() {
        return "unicode whitespaces and invisible separators";
    }
}