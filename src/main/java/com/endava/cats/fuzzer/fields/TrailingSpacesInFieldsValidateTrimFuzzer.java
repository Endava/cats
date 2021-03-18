package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@FieldFuzzer
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='validateAndTrim' and ${fuzzer.fields.TrailingSpacesInFieldsFuzzer.enabled}")
public class TrailingSpacesInFieldsValidateTrimFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    @Autowired
    public TrailingSpacesInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "trailing spaces";
    }

    @Override
    protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return FuzzingStrategy.trail().withData(" ");
    }

    /**
     * Fields used as discriminators will not be fuzzed with leading spaces as they are usually used by marshalling frameworks to choose sub-types.
     *
     * @param data
     * @param fuzzedField
     * @param fuzzingStrategy
     * @return
     */
    protected boolean isFuzzingPossibleSpecificToFuzzer(FuzzingData data, String fuzzedField, FuzzingStrategy fuzzingStrategy) {
        return !PayloadGenerator.GlobalData.getDiscriminators().contains(fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with trailing spaces in the targeted field";
    }
}
