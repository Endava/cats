package com.endava.cats.fuzzer.fields.within;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
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
@SanitizeAndValidate
public class WithinMultiCodePointEmojisInStringFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    protected WithinMultiCodePointEmojisInStringFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return CommonWithinMethods.getFuzzingStrategies(data, fuzzedField, this.getInvisibleChars(), false);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values containing multi code point emojis";
    }

    @Override
    public List<String> getInvisibleChars() {
        return UnicodeGenerator.getMultiCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }
}
