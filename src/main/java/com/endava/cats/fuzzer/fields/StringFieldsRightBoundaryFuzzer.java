package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer class targeting right boundary conditions for string fields.
 * Extends the {@link BaseBoundaryFieldFuzzer} class and provides a constructor
 * to initialize common dependencies for fuzzing string fields with the expectation of right boundary conditions.
 */
@Singleton
@FieldFuzzer
public class StringFieldsRightBoundaryFuzzer extends BaseBoundaryFieldFuzzer {

    /**
     * Constructor for initializing common dependencies for fuzzing string fields with the expectation of right boundary conditions.
     *
     * @param sc The {@link ServiceCaller} used to make service calls.
     * @param lr The {@link TestCaseListener} for reporting test case events.
     * @param cu The {@link CatsUtil} for utility functions related to CATS (Compliance and Testing Suite).
     * @param cp The {@link FilesArguments} for file-related arguments.
     */
    public StringFieldsRightBoundaryFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("string");
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        return StringGenerator.generateRightBoundString(schema);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema<?> schema = data.getRequestPropertyTypes().get(fuzzedField);
        if (schema.getMaxLength() != null && schema.getMaxLength() == Integer.MAX_VALUE) {
            logger.warn("{} seems to use default maxLength. Please consider explicitly setting a reasonable value for maxLength.", fuzzedField);
            logger.skip("Skipping due to: maxLength for {} already at the upper limit!", fuzzedField);
        }
        return schema.getMaxLength() != null && schema.getMaxLength() < Integer.MAX_VALUE;
    }


    @Override
    public String description() {
        return "iterate through each String field and send outside the range values on the right side";
    }
}