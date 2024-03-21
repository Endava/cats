package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly4XXBaseFieldsFuzzer;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

/**
 * Fuzzer that sends very large integers in numeric fields. Size of the large
 * integer is controlled by the {@code --largeStringsSize} argument.
 */
@Singleton
@FieldFuzzer
public class VeryLargeIntegersInNumericFieldsFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    final ProcessingArguments processingArguments;

    /**
     * Creates a new VeryLargeIntegersInNumericFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param pa to get the size of the large integers
     */
    public VeryLargeIntegersInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cp);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "very large numbers";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema<?> fieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        if (CatsModelUtils.isNumberSchema(fieldSchema) || CatsModelUtils.isIntegerSchema(fieldSchema)) {
            return Collections.singletonList(FuzzingStrategy.replace().withData(this.getTheActualData()));
        } else {
            return List.of(FuzzingStrategy.skip().withData("field is not numeric"));
        }
    }

    public String getTheActualData() {
        return NumberGenerator.generateVeryLargeInteger(processingArguments.getLargeStringsSize() / 4);
    }

    @Override
    protected boolean shouldMatchResponseSchema(FuzzingData data) {
        return HttpMethod.requiresBody(data.getMethod());
    }

    @Override
    protected boolean shouldMatchContentType(FuzzingData data) {
        return HttpMethod.requiresBody(data.getMethod());
    }

    @Override
    public String description() {
        return "iterate through each numeric field and send very large numbers (40000 characters)";
    }
}