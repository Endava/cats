package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;


/**
 * Fuzzer class targeting left boundary conditions for string fields.
 * Extends the {@link BaseBoundaryFieldFuzzer} class and provides a constructor
 * to initialize common dependencies for fuzzing string fields with the expectation of left boundary conditions.
 */
@Singleton
@FieldFuzzer
public class StringFieldsLeftBoundaryFuzzer extends BaseBoundaryFieldFuzzer {

    /**
     * Constructor for initializing common dependencies for fuzzing string fields with the expectation of left boundary conditions.
     *
     * @param sc The {@link ServiceCaller} used to make service calls.
     * @param lr The {@link TestCaseListener} for reporting test case events.
     * @param cp The {@link FilesArguments} for file-related arguments.
     */
    public StringFieldsLeftBoundaryFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
        super(sc, lr, cp);
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("string");
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        return StringGenerator.generateLeftBoundString(schema);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return "iterate through each String field and send outside the range values on the left side";
    }
}
