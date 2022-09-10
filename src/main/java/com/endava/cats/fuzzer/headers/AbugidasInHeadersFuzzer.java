package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.PayloadUtils;
import com.endava.cats.report.TestCaseListener;

import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@HeaderFuzzer
@Singleton
public class AbugidasInHeadersFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {

    public AbugidasInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "abugidas chars";
    }

    @Override
    protected List<FuzzingStrategy> fuzzStrategy() {
        return PayloadUtils.getAbugidasChars().stream().map(value -> FuzzingStrategy.replace().withData(value)).toList();
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with abugidas chars in the targeted header";
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}