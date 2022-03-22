package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.PayloadUtils;
import com.endava.cats.report.TestCaseListener;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

@HeaderFuzzer
@Singleton
public class ZalgoTextInHeadersFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {

    public ZalgoTextInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "zalgo text";
    }

    @Override
    protected List<FuzzingStrategy> fuzzStrategy() {
        return Collections.singletonList(FuzzingStrategy.replace().withData(PayloadUtils.getZalgoText()));
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with zalgo text in the targeted header";
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}