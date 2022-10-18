package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly4XXBaseFieldsFuzzer;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

@Singleton
@FieldFuzzer
public class VeryLargeIntegersInNumericFieldsFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    final ProcessingArguments processingArguments;

    public VeryLargeIntegersInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cu, cp);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "very large numbers";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema<?> fieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        if (fieldSchema instanceof NumberSchema || fieldSchema instanceof IntegerSchema) {
            return Collections.singletonList(FuzzingStrategy.replace().withData(this.getTheActualData()));
        } else {
            return List.of(FuzzingStrategy.skip().withData("field is not numeric"));
        }
    }

    public String getTheActualData() {
        return NumberGenerator.generateVeryLargeInteger(processingArguments.getLargeStringsSize() / 4);
    }

    @Override
    public String description() {
        return "iterate through each numeric field and send very large numbers (40000 characters)";
    }
}