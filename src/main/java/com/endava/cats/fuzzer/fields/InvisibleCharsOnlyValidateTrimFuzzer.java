package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import java.util.Arrays;
import java.util.List;

public abstract class InvisibleCharsOnlyValidateTrimFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    protected InvisibleCharsOnlyValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return this.getInvisibleCharDescription() + " only";
    }

    @Override
    protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return PayloadGenerator.getFuzzStrategyWithRepeatedCharacterReplacingValidValue(data, fuzzedField, this.getInvisibleChar());
    }

    @Override
    public List<HttpMethod> skipFor() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with " + this.getInvisibleCharDescription() + " in the targeted field";
    }

    abstract String getInvisibleChar();

    abstract String getInvisibleCharDescription();
}

