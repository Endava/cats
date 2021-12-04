package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.SpacesCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
public class TrailingSpacesInHeadersFuzzer extends SpacesCharsBaseFuzzer {

    protected TrailingSpacesInHeadersFuzzer(CatsUtil cu, ServiceCaller sc, TestCaseListener lr) {
        super(cu, sc, lr);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "prefix values with spaces";
    }

    @Override
    public List<String> getInvisibleChars() {
        return catsUtil.getSpacesHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.trail();
    }
}
