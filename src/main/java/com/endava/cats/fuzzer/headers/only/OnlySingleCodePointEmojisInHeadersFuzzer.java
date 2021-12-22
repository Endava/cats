package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.EmojiFuzzer;
import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.PayloadUtils;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@EmojiFuzzer
public class OnlySingleCodePointEmojisInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public OnlySingleCodePointEmojisInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "replace value with single code point emojis";
    }

    @Override
    public List<String> getInvisibleChars() {
        return PayloadUtils.getSingleCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }

    @Override
    protected boolean matchResponseSchema() {
        return false;
    }
}