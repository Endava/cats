package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.WhitespaceFuzzer;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.PayloadUtils;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@WhitespaceFuzzer
public class OnlyWhitespacesInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public OnlyWhitespacesInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "replace value with unicode separators";
    }

    @Override
    public List<String> getInvisibleChars() {
        return PayloadUtils.getSeparatorsHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }
}
