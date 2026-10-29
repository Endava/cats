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
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends a compact set of high-impact control characters (NUL byte, BEL, BS, ESC, Unicode non-character)
 * in string fields for services following the sanitize-then-validate strategy.
 */
@FieldFuzzer
@Singleton
@SanitizeAndValidate
public class ControlCharsInStringFieldsSanitizeValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {

    /**
     * Creates a new ControlCharsInStringFieldsSanitizeValidateFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    protected ControlCharsInStringFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "control characters";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return UnicodeGenerator.getControlCharsSmallListFields().stream()
                .map(fuzzVal -> FuzzingStrategy.insert().withData(fuzzVal))
                .toList();
    }

    @Override
    protected boolean shouldCheckForFuzzedValueMatchingPattern() {
        return false;
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        Schema<?> schema = data.getRequestPropertyTypes().get(fuzzedField);
        return CatsModelUtils.isStringSchema(schema) && super.isFieldSkippableForSpecialCharsFuzzers(data, fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each string field and send values containing NUL bytes and control characters";
    }
}
