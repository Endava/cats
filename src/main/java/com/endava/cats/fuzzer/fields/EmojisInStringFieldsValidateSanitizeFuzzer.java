package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.ValidateAndSanitize;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends 4-byte supplementary plane and ZWJ emojis in string fields
 * for services following the validate-then-sanitize strategy.
 */
@Singleton
@FieldFuzzer
@ValidateAndSanitize
public class EmojisInStringFieldsValidateSanitizeFuzzer extends EmojisInStringFieldsSanitizeValidateFuzzer {

    /**
     * Creates a new EmojisInStringFieldsValidateSanitizeFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    protected EmojisInStringFieldsValidateSanitizeFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return FuzzingStrategy.getFuzzingStrategies(data, fuzzedField, UnicodeGenerator.getEmojisSmallListFields(), true);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamilyPredefined.FOURXX;
    }
}
