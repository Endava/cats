package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.SpacesCharsBaseFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
public class OnlySpacesInHeadersFuzzer extends SpacesCharsBaseFuzzer {

    protected OnlySpacesInHeadersFuzzer(CatsUtil cu, ServiceCaller sc, TestCaseListener lr) {
        super(cu, sc, lr);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "replace value with spaces";
    }

    @Override
    public List<String> getInvisibleChars() {
        return catsUtil.getSpacesHeaders();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }
}
