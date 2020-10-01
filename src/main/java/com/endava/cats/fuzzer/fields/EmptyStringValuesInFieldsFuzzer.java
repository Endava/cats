package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@FieldFuzzer
public class EmptyStringValuesInFieldsFuzzer extends Expect4XXForRequiredBaseFieldsFuzzer {

    @Autowired
    public EmptyStringValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        super(sc, lr, cu);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "empty strings";
    }

    @Override
    protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return FuzzingStrategy.replace().withData("");
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public List<HttpMethod> skipFor() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with empty String values in the targeted field";
    }
}
