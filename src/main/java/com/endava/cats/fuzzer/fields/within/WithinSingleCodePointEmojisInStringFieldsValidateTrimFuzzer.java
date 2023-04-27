package com.endava.cats.fuzzer.fields.within;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.ValidateAndSanitize;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.strategy.CommonWithinMethods;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
@EmojiFuzzer
@ValidateAndSanitize
public class WithinSingleCodePointEmojisInStringFieldsValidateTrimFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    protected WithinSingleCodePointEmojisInStringFieldsValidateTrimFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return ResponseCodeFamily.FOURXX;
    }


    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return CommonWithinMethods.getFuzzingStrategies(data, fuzzedField, this.getInvisibleChars(), true);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values containing single code point emojis";
    }

    @Override
    public List<String> getInvisibleChars() {
        return UnicodeGenerator.getSingleCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }
}