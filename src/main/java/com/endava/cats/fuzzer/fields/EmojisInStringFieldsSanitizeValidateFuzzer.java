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
 * Fuzzer that sends 4-byte supplementary plane and ZWJ emojis in string fields
 * for services following the sanitize-then-validate strategy.
 */
@Singleton
@FieldFuzzer
@SanitizeAndValidate
public class EmojisInStringFieldsSanitizeValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {

    /**
     * Creates a new EmojisInStringFieldsSanitizeValidateFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    protected EmojisInStringFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return FuzzingStrategy.getFuzzingStrategies(data, fuzzedField, UnicodeGenerator.getEmojisSmallListFields(), false);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values containing 4-byte supplementary plane emojis";
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return super.isFieldSkippableForSpecialCharsFuzzers(data, fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send " + typeOfDataSentToTheService();
    }
}
