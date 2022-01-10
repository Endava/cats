package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.model.util.PayloadUtils;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@HeaderFuzzer
public class VeryLargeUnicodeValuesInHeadersFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {
    private final ProcessingArguments processingArguments;

    public VeryLargeUnicodeValuesInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr, ProcessingArguments pa) {
        super(sc, lr);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "large unicode values";
    }

    @Override
    protected List<FuzzingStrategy> fuzzStrategy() {
        return PayloadUtils.getLargeValuesStrategy(processingArguments.getLargeStringsSize());
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with large unicode values in the targeted header";
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}