package com.endava.cats.fuzzer.fields.trailing;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.ExpectOnly4XXBaseFieldsFuzzer;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import java.util.List;
import java.util.stream.Collectors;

public abstract class TrailingInvisibleCharsValidateTrimFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    protected TrailingInvisibleCharsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "trailing " + this.getInvisibleCharDescription();
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return this.getInvisibleChars()
                .stream().map(value -> FuzzingStrategy.trail().withData(value))
                .collect(Collectors.toList());
    }

    @Override
    protected boolean isFuzzingPossibleSpecificToFuzzer(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        return !PayloadGenerator.GlobalData.getDiscriminators().contains(fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with trailing " + this.getInvisibleCharDescription() + " in the targeted field";
    }

    abstract List<String> getInvisibleChars();

    abstract String getInvisibleCharDescription();
}
