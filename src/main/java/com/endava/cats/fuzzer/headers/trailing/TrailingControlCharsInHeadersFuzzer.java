package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.annotations.ControlCharFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.model.util.PayloadUtils;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@HeaderFuzzer
@ControlCharFuzzer
public class TrailingControlCharsInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public TrailingControlCharsInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "trail values with control chars";
    }

    @Override
    public List<String> getInvisibleChars() {
        List<String> controlChars = new ArrayList<>(PayloadUtils.getControlCharsHeaders());
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