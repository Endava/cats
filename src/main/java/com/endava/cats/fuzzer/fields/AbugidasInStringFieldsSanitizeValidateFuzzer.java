package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly2XXBaseFieldsFuzzer;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.CommonWithinMethods;
import com.endava.cats.strategy.FuzzingStrategy;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends abugidas characters in string fields for services following the sanitize then validate strategy.
 */
@Singleton
@FieldFuzzer
@SanitizeAndValidate
public class AbugidasInStringFieldsSanitizeValidateFuzzer extends ExpectOnly2XXBaseFieldsFuzzer {

    /**
     * Creates a new AbugidasInStringFieldsSanitizeValidateFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    protected AbugidasInStringFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema<?> fuzzedFieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        return CommonWithinMethods.getFuzzingStrategies(fuzzedFieldSchema, UnicodeGenerator.getAbugidasChars(), false);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values containing abugidas chars";
    }

    @Override
    public boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        Schema<?> fuzzedFieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        boolean isRefDataField = filesArguments.getRefData(data.getPath()).get(fuzzedField) != null;
        return testCaseListener.isFieldNotADiscriminator(fuzzedField) && fuzzedFieldSchema.getEnum() == null && !isRefDataField;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return ResponseCodeFamily.FOURXX_TWOXX;
    }

    @Override
    public String description() {
        return "iterate through each field and send " + typeOfDataSentToTheService();
    }
}
