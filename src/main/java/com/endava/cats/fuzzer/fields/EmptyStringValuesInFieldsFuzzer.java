package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.Expect4XXForRequiredBaseFieldsFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@FieldFuzzer
@ConditionalOnProperty(value = "fuzzer.fields.EmptyStringValuesInFieldsFuzzer.enabled", havingValue = "true")
public class EmptyStringValuesInFieldsFuzzer extends Expect4XXForRequiredBaseFieldsFuzzer {
    private final FilterArguments filterArguments;

    @Autowired
    public EmptyStringValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, FilterArguments fa) {
        super(sc, lr, cu, cp);
        this.filterArguments = fa;
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "empty strings";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return Collections.singletonList(FuzzingStrategy.replace().withData(""));
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public List<String> skipForFields() {
        return filterArguments.getSkippedFields();
    }

    @Override
    public String description() {
        return "iterate through each field and send requests with empty String values in the targeted field";
    }
}
