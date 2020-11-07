package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@FieldFuzzer
@ConditionalOnProperty(value = "edgeSpacesStrategy", havingValue = "validateAndTrim")
public class SpacesOnlyInFieldsValidateTrimFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    @Autowired
    public SpacesOnlyInFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, CatsParams cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "spaces only";
    }

    @Override
    protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return SpacesOnlyInFieldsTrimValidateFuzzer.getFuzzStrategy(data, fuzzedField);
    }

    @Override
    public List<HttpMethod> skipFor() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with spaces in the targeted field";
    }
}
