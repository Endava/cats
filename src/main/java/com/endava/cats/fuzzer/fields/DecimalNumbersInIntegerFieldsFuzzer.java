package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingConstraints;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends decimal numbers in numeric fields.
 */
@Singleton
@FieldFuzzer
public class DecimalNumbersInIntegerFieldsFuzzer extends BaseBoundaryFieldFuzzer {
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new DecimalNumbersInIntegerFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    public DecimalNumbersInIntegerFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        this(sc, lr, cp, null);
    }

    /**
     * Creates a new DecimalNumbersInIntegerFieldsFuzzer instance with processing arguments.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param pa processing arguments
     */
    @Inject
    public DecimalNumbersInIntegerFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cp);
        this.processingArguments = pa;
    }

    @Override
    protected ResponseCodeFamily getExpectedResponseCodeBasedOnConstraints(
            FuzzingData data, String fuzzedField, boolean isFuzzedValueMatchingPattern, FuzzingConstraints fuzzingConstraints) {
        if (processingArguments != null && !processingArguments.isStrictTypes()) {
            return ResponseCodeFamilyPredefined.FOURXX_TWOXX;
        }
        return super.getExpectedResponseCodeBasedOnConstraints(data, fuzzedField, isFuzzedValueMatchingPattern, fuzzingConstraints);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "decimal values in integer fields";
    }


    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("integer");
    }

    @Override
    public Number getBoundaryValue(Schema schema) {
        return NumberGenerator.generateDecimalValue(schema);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return "iterate through each Integer field and send decimal values";
    }
}