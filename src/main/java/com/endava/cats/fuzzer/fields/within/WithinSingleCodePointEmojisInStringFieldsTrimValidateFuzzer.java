package com.endava.cats.fuzzer.fields.within;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.CommonWithinMethods;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@FieldFuzzer
@EmojiFuzzer
@SanitizeAndValidate
public class WithinSingleCodePointEmojisInStringFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    protected WithinSingleCodePointEmojisInStringFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema<?> fuzzedFieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        return CommonWithinMethods.getFuzzingStrategies(fuzzedFieldSchema, this.getInvisibleChars(), false);
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
