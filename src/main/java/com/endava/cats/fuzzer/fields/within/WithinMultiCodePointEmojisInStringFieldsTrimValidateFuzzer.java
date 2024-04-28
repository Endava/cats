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
import com.endava.cats.strategy.FuzzingStrategy;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that inserts multi code point emojis in valid field values.
 */
@Singleton
@FieldFuzzer
@EmojiFuzzer
@SanitizeAndValidate
public class WithinMultiCodePointEmojisInStringFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    /**
     * Creates a new WithinMultiCodePointEmojisInStringFieldsTrimValidateFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    protected WithinMultiCodePointEmojisInStringFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema<?> fuzzedFieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        return FuzzingStrategy.getFuzzingStrategies(fuzzedFieldSchema, this.getInvisibleChars(), false);
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
