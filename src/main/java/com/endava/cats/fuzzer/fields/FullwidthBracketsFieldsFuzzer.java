package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly2XXBaseFieldsFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Inserts fullwidth angle brackets to bypass simplistic filter checks.
 */
@FieldFuzzer
@Singleton
class FullwidthBracketsFieldsFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {
    public FullwidthBracketsFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "fullwidth angle bracket characters";
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return super.isFieldSkippableForSpecialCharsFuzzers(data, fuzzedField);
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        List<String> brackets = List.of("\uFF1C", "\uFF1E");
        return FuzzingStrategy.getFuzzingStrategies(data, fuzzedField, brackets, true);

    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public String description() {
        return "insert fullwidth '<' and '>' to test for markup filter bypass";
    }
}
