package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@HeaderFuzzer
@ConditionalOnProperty(value = "fuzzer.headers.TrailingWhitespacesInHeadersFuzzer.enabled", havingValue = "true")
public class TrailingWhitespacesInHeadersFuzzer extends TrailingInvisibleCharsFuzzer {

    @Autowired
    public TrailingWhitespacesInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "trail values with whitespaces";
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.WHITESPACES_HEADERS;
    }
}
