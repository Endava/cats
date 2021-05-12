package com.endava.cats.fuzzer.fields.only;

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
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='trimAndValidate' and ${fuzzer.fields.WhitespacesOnlyInFieldsFuzzer.enabled}")
public class WhitespacesOnlyInFieldsTrimValidateFuzzer extends InvisibleCharsOnlyTrimValidateFuzzer {

    @Autowired
    public WhitespacesOnlyInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.WHITESPACES;
    }

    @Override
    String getInvisibleCharDescription() {
        return "unicode whitespaces and invisible separators";
    }
}
