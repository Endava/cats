package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.ExpectOnly4XXBaseFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.model.util.PayloadUtils;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
public class VeryLargeUnicodeValuesInFieldsFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    private final ProcessingArguments processingArguments;

    public VeryLargeUnicodeValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cu, cp);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "very large unicode values";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return PayloadUtils.getLargeValuesStrategy(processingArguments.getLargeStringsSize());
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with very large random unicode values in the targeted field";
    }
}