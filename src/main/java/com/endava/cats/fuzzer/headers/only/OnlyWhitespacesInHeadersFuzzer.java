package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@HeaderFuzzer
@ConditionalOnProperty(value = "fuzzer.headers.OnlyWhitespacesInHeadersFuzzer.enabled", havingValue = "true")
public class OnlyWhitespacesInHeadersFuzzer extends InvisibleCharsOnlyFuzzer {
    public OnlyWhitespacesInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "replace value with whitespaces";
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.WHITESPACES_HEADERS;
    }
}
