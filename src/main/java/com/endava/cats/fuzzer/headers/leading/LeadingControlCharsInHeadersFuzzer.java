package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.PayloadUtils;
import com.endava.cats.report.TestCaseListener;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@ControlCharFuzzer
public class LeadingControlCharsInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public LeadingControlCharsInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public List<String> getInvisibleChars() {
        return PayloadUtils.getControlCharsHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "prefix values with control chars";
    }

    @Override
    protected boolean matchResponseSchema() {
        return false;
    }

}
