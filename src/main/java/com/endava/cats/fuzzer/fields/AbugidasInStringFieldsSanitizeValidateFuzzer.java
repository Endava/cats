package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.SanitizeAndValidate;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CommonWithinMethods;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.PayloadUtils;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@FieldFuzzer
@SanitizeAndValidate
public class AbugidasInStringFieldsSanitizeValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    protected AbugidasInStringFieldsSanitizeValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema<?> fuzzedFieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        return PayloadUtils.getAbugidasChars()
                .stream()
                .map(abugidasChar -> CommonWithinMethods.getTextBasedOnMaxSize(fuzzedFieldSchema, abugidasChar))
                .collect(Collectors.toList());
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values containing abugidas chars";
    }

    @Override
    public List<String> getInvisibleChars() {
        return Collections.emptyList();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.replace();
    }
}
