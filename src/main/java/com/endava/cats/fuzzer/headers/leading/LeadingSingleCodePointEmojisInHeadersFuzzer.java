package com.endava.cats.fuzzer.headers.leading;

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
public class LeadingSingleCodePointEmojisInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public LeadingSingleCodePointEmojisInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public List<String> getInvisibleChars() {
        return PayloadUtils.getSingleCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "prefix values with single code point emojis";
    }

    @Override
    protected boolean matchResponseSchema() {
        return false;
    }
}
