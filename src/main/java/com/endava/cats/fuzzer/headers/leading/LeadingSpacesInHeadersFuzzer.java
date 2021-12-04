package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.SpacesCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@HeaderFuzzer
public class LeadingSpacesInHeadersFuzzer extends SpacesCharsBaseFuzzer {

    protected LeadingSpacesInHeadersFuzzer(CatsUtil cu, ServiceCaller sc, TestCaseListener lr) {
        super(cu, sc, lr);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "trail value with spaces";
    }

    @Override
    public List<String> getInvisibleChars() {
        List<String> leadingChars = new ArrayList<>(catsUtil.getSpacesHeaders());
        leadingChars.remove("\r");
        return leadingChars;
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }
}
