package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExactValuesInFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.Function;

/**
 * Fuzzer that sends maximum exact numbers in numeric fields if they have 'maximum' defined.
 */
@Singleton
@FieldFuzzer
public class MaximumExactNumbersInNumericFieldsFuzzer extends ExactValuesInFieldsFuzzer {

    /**
     * Creates a new MaximumExactNumbersInNumericFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    public MaximumExactNumbersInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    protected String exactValueTypeString() {
        return "maximum";
    }

    @Override
    protected Function<Schema, Number> getExactMethod() {
        return Schema::getMaximum;
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("number", "integer");
    }

    @Override
    public Object getBoundaryValue(Schema schema) {
        return getExactMethod().apply(schema);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        boolean isRefDataField = filesArguments.getRefData(data.getPath()).get(fuzzedField) != null;
        return !isRefDataField && getExactMethod().apply(schema) != null;
    }
}
