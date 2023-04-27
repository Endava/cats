package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly4XXBaseFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
public class VeryLargeUnicodeStringsInFieldsFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    private final ProcessingArguments processingArguments;

    public VeryLargeUnicodeStringsInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cu, cp);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "very large unicode values";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return FuzzingStrategy.getLargeValuesStrategy(processingArguments.getLargeStringsSize());
    }

    @Override
    public String description() {
        return "iterate through each field and send very large random unicode values";
    }
}