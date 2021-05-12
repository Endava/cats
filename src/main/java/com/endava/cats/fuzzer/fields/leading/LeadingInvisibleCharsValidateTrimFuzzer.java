package com.endava.cats.fuzzer.fields.leading;

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

public abstract class LeadingInvisibleCharsValidateTrimFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    protected LeadingInvisibleCharsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "data prefixed with " + this.getInvisibleCharDescription();
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return this.getInvisibleChars()
                .stream().map(value -> FuzzingStrategy.prefix().withData(value))
                .collect(Collectors.toList());
    }

    /**
     * Fields used as discriminators will not be fuzzed with leading spaces as they are usually used by marshalling frameworks to choose sub-types.
     *
     * @param data
     * @param fuzzedField
     * @param fuzzingStrategy
     * @return
     */
    @Override
    protected boolean isFuzzingPossibleSpecificToFuzzer(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        return !PayloadGenerator.GlobalData.getDiscriminators().contains(fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with " + this.getInvisibleCharDescription() + " prefixing the current value in the targeted field";
    }

    abstract List<String> getInvisibleChars();

    abstract String getInvisibleCharDescription();
}