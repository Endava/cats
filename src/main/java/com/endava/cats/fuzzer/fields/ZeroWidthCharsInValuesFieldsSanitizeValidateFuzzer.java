package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly2XXBaseFieldsFuzzer;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends zero-width characters in fields for the sanitize then validate strategy.
 */
@FieldFuzzer
@Singleton
@SanitizeAndValidate
public class ZeroWidthCharsInValuesFieldsSanitizeValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {
    /**
     * Constructor for initializing common dependencies for fuzzing base fields with the expectation of only 4xx responses.
     *
     * @param sc The {@link ServiceCaller} used to make service calls.
     * @param lr The {@link TestCaseListener} for reporting test case events.
     * @param cp The {@link FilesArguments} for file-related arguments.
     */
    protected ZeroWidthCharsInValuesFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "zero-width characters";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return UnicodeGenerator.getZwCharsSmallListFields().stream()
                .map(fuzzVal -> FuzzingStrategy.insert().withData(fuzzVal)).toList();
    }

    @Override
    protected boolean shouldCheckForFuzzedValueMatchingPattern() {
        return false;
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return testCaseListener.isFieldNotADiscriminator(fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send values containing zero-width characters";
    }
}
