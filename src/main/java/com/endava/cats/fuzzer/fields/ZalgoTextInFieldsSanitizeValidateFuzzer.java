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
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends zalgo text in fields for the sanitize then validate strategy.
 */
@Singleton
@FieldFuzzer
@SanitizeAndValidate
public class ZalgoTextInFieldsSanitizeValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {

    /**
     * Creates a new ZalgoTextInFieldsSanitizeValidateFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cu utility class
     * @param cp files arguments
     */
    protected ZalgoTextInFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values containing zalgo text";
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return List.of(FuzzingStrategy.prefix().withData(UnicodeGenerator.getZalgoText()));
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return testCaseListener.isFieldNotADiscriminator(fuzzedField);
    }

    @Override
    public String description() {
        return "iterate through each field and send " + typeOfDataSentToTheService();
    }
}