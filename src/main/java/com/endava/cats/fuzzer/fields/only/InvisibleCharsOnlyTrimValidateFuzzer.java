package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.fields.base.Expect4XXForRequiredBaseFieldsFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class InvisibleCharsOnlyTrimValidateFuzzer extends Expect4XXForRequiredBaseFieldsFuzzer {
    private final FilterArguments filterArguments;

    protected InvisibleCharsOnlyTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, FilterArguments fa) {
        super(sc, lr, cu, cp);
        this.filterArguments = fa;
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return this.getInvisibleChars().stream()
                .map(value -> PayloadGenerator.getFuzzStrategyWithRepeatedCharacterReplacingValidValue(data, fuzzedField, value))
                .collect(Collectors.toList());

    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }

    /**
     * Supplied skipped fields are skipped when we only sent invalid data.
     *
     * @return the list with skipped fields.
     */
    @Override
    public List<String> skipForFields() {
        return filterArguments.getSkippedFields();
    }

    @Override
    public String description() {
        return "iterate through each field and send  " + this.typeOfDataSentToTheService();
    }

    abstract List<String> getInvisibleChars();
}