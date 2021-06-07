package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import java.util.List;
import java.util.stream.Collectors;

public abstract class InvisibleCharsBaseTrimValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {

    protected InvisibleCharsBaseTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return this.getInvisibleChars()
                .stream().map(value -> concreteFuzzStrategy().withData(value))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isFuzzingPossibleSpecificToFuzzer(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        return !PayloadGenerator.GlobalData.getDiscriminators().contains(fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send " + this.typeOfDataSentToTheService();
    }


    public abstract List<String> getInvisibleChars();

    public abstract FuzzingStrategy concreteFuzzStrategy();
}
