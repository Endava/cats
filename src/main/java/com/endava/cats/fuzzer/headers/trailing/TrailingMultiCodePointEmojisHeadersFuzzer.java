package com.endava.cats.fuzzer.headers.trailing;

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
public class TrailingMultiCodePointEmojisHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public TrailingMultiCodePointEmojisHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "trail values with multi code point emojis";
    }

    @Override
    public List<String> getInvisibleChars() {
        return PayloadUtils.getMultiCodePointEmojis();
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
