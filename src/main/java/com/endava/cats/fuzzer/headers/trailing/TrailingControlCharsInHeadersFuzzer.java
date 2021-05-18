package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@HeaderFuzzer
@ConditionalOnProperty(value = "fuzzer.headers.TrailingControlCharsInHeadersFuzzer.enabled", havingValue = "true")
public class TrailingControlCharsInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    @Autowired
    public TrailingControlCharsInHeadersFuzzer(CatsUtil cu, ServiceCaller sc, TestCaseListener lr) {
        super(cu, sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "trail values with control chars";
    }

    @Override
    public List<String> getInvisibleChars() {
        List<String> controlChars = new ArrayList<>(catsUtil.getControlCharsHeaders());
        controlChars.remove("\r");

        return controlChars;
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.trail();
    }

    @Override
    protected boolean matchResponseSchema() {
        return false;
    }
}