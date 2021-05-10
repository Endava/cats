package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@FieldFuzzer
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}' == 'trimAndValidate' and ${fuzzer.fields.TrailingTabsInFieldsFuzzer.enabled}")
public class TrailingTabsInFieldsTrimValidateFuzzer extends TrailingInvisibleCharsTrimValidateFuzzer {

    @Autowired
    protected TrailingTabsInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    String getInvisibleChar() {
        return "\t";
    }

    @Override
    String getInvisibleCharDescription() {
        return "tabs";
    }
}