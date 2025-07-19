package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly2XXBaseFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Inserts Hangul Filler characters into field values.
 */
@FieldFuzzer
@Singleton
@SanitizeAndValidate
public class HangulFillerFieldsSanitizeValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {
    public HangulFillerFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "Hangul filler characters";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        List<String> fillers = List.of("\u3164", "\uFFA0", "\u115F", "\u1160");
        return FuzzingStrategy.getFuzzingStrategies(data, fuzzedField, fillers, true);
    }

    @Override
    protected boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return super.isFieldSkippableForSpecialCharsFuzzers(data, fuzzedField);
    }

    @Override
    public String description() {
        return "Inject Hangul filler characters to test for hidden-input handling";
    }
}
