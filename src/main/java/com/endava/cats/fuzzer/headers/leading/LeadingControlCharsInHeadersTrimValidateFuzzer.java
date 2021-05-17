package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@HeaderFuzzer
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='trimAndValidate' and ${fuzzer.headers.LeadingControlCharsInHeadersFuzzer.enabled}")
public class LeadingControlCharsInHeadersTrimValidateFuzzer extends LeadingInvisibleCharsTrimValidateFuzzer {

    @Autowired
    public LeadingControlCharsInHeadersTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.CONTROL_CHARS;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "prefix values with control chars";
    }
}
