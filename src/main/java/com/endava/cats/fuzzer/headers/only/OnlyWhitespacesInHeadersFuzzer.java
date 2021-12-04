package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.WhitespaceFuzzer;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@WhitespaceFuzzer
public class OnlyWhitespacesInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public OnlyWhitespacesInHeadersFuzzer(CatsUtil cu, ServiceCaller sc, TestCaseListener lr) {
        super(cu, sc, lr);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "replace value with unicode separators";
    }

    @Override
    public List<String> getInvisibleChars() {
        return catsUtil.getSeparatorsHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }
}
