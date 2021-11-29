package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.headers.base.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@HeaderFuzzer
public class VeryLargeUnicodeValuesInHeadersFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {
    private final ProcessingArguments processingArguments;

    @Autowired
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
        return CatsUtil.getLargeValuesStrategy(processingArguments.getLargeStringsSize());
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with large unicode values in the targeted header";
    }
}