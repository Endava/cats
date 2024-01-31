package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExactValuesInFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.function.Function;

/**
 * Fuzzer that min length exact values in string fields if 'minLength' property is defined.
 */
@Singleton
@FieldFuzzer
public class MinLengthExactValuesInStringFieldsFuzzer extends ExactValuesInFieldsFuzzer {

    /**
     * Creates a new MinLengthExactValuesInStringFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cu utility class
     * @param cp files arguments
     */
    public MinLengthExactValuesInStringFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String exactValueTypeString() {
        return "minLength";
    }

    @Override
    public Function<Schema, Number> getExactMethod() {
        return Schema::getMinLength;
    }
}