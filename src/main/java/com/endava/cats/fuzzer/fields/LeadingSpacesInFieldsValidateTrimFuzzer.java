package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@FieldFuzzer
@ConditionalOnProperty(value = "edgeSpacesStrategy", havingValue = "validateAndTrim")
public class LeadingSpacesInFieldsValidateTrimFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    @Autowired
    public LeadingSpacesInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        super(sc, lr, cu);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "data prefixed with spaces";
    }

    @Override
    protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return FuzzingStrategy.prefix().withData(" ");
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with spaces prefixing the current value in the targeted field";
    }
}