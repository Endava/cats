package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly2XXBaseFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.PayloadUtils;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
@SanitizeAndValidate
public class ZalgoTextInStringFieldsSanitizeValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {

    protected ZalgoTextInStringFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values containing zalgo text";
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return List.of(FuzzingStrategy.prefix().withData(PayloadUtils.getZalgoText()));
    }

    @Override
    public boolean isFuzzingPossibleSpecificToFuzzer(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        return testCaseListener.isFieldNotADiscriminator(fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send " + typeOfDataSentToTheService();
    }
}