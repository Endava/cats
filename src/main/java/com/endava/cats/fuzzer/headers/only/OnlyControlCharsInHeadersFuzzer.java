package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.ControlCharFuzzer;
import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.InvisibleCharsBaseFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
@ControlCharFuzzer
public class OnlyControlCharsInHeadersFuzzer extends InvisibleCharsBaseFuzzer {

    public OnlyControlCharsInHeadersFuzzer(CatsUtil cu, ServiceCaller sc, TestCaseListener lr) {
        super(cu, sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "replace value with control chars";
    }

    @Override
    public List<String> getInvisibleChars() {
        return catsUtil.getControlCharsHeaders();
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
