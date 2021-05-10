package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

public abstract class TrailingInvisibleCharsTrimValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {

    protected TrailingInvisibleCharsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "trailing " + this.getInvisibleCharDescription();
    }

    @Override
    protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return FuzzingStrategy.trail().withData(this.getInvisibleChar());
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.TWOXX;
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
        return "iterate through each field and send requests with trailing " + this.getInvisibleCharDescription() + " in the targeted field";
    }

    abstract String getInvisibleChar();

    abstract String getInvisibleCharDescription();
}
