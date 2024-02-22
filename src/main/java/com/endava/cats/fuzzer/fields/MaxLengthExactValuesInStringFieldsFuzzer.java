package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExactValuesInFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.function.Function;

/**
 * Fuzzer that sends max length exact values in string fields if they have 'maxLength' defined.
 */
@Singleton
@FieldFuzzer
public class MaxLengthExactValuesInStringFieldsFuzzer extends ExactValuesInFieldsFuzzer {

    /**
     * Creates a new MaxLengthExactValuesInStringFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     */
    public MaxLengthExactValuesInStringFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    protected String exactValueTypeString() {
        return "maxLength";
    }

    @Override
    public Function<Schema, Number> getExactMethod() {
        return Schema::getMaxLength;
    }
}