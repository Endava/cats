package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@HeaderFuzzer
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='trimAndValidate' and ${fuzzer.headers.WhitespacesOnlyInHeadersFuzzer.enabled}")
public class WhitespacesOnlyInHeadersTrimValidateFuzzer extends InvisibleCharsOnlyTrimValidateFuzzer {
    public WhitespacesOnlyInHeadersTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "replace value with whitespaces";
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.WHITESPACES;
    }
}
