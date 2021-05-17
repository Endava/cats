package com.endava.cats.fuzzer.headers.trailing;

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
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='trimAndValidate' and ${fuzzer.headers.TrailingControlCharsInHeadersFuzzer.enabled}")
public class TrailingControlCharsInHeadersTrimValidateFuzzer extends TrailingInvisibleCharsTrimValidateFuzzer {

    @Autowired
    public TrailingControlCharsInHeadersTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "trail values with control chars";
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.CONTROL_CHARS;
    }
}