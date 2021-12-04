package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.ExpectOnly4XXBaseFieldsFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

@Singleton
@FieldFuzzer
public class VeryLargeValuesInFieldsFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    private final ProcessingArguments processingArguments;

    public VeryLargeValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cu, cp);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "very large string values";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return Collections.singletonList(
                FuzzingStrategy.replace().withData(
                        StringGenerator.generateLargeString(processingArguments.getLargeStringsSize() / 4)));
    }

    @Override
    public String description() {
        return "iterate through each String field and send requests with very large values (40000 characters) in the targeted field";
    }
}