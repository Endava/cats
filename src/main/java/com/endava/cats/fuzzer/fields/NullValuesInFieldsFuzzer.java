package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.fuzzer.fields.base.Expect4XXForRequiredBaseFieldsFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;

import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.List;

@Singleton
@FieldFuzzer
public class NullValuesInFieldsFuzzer extends Expect4XXForRequiredBaseFieldsFuzzer {
    private final IgnoreArguments ignoreArguments;

    public NullValuesInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, IgnoreArguments fa) {
        super(sc, lr, cu, cp);
        this.ignoreArguments = fa;
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "NULL values";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return Collections.singletonList(FuzzingStrategy.replace().withData(null));
    }

    @Override
    protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.TWOXX;
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return HttpMethod.requiresBody(data.getMethod()) || data.isQueryParam(fuzzedField);
    }

    @Override
    public List<String> skipForFields() {
        return ignoreArguments.getSkipFields();
    }

    @Override
    public String description() {
        return "iterate through each field and send null values";
    }
}
