package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.ExactValuesInFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.function.Function;

@Singleton
@FieldFuzzer
public class MinimumExactNumbersInNumericFieldsFuzzer extends ExactValuesInFieldsFuzzer {

    public MinimumExactNumbersInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String exactValueTypeString() {
        return "minimum";
    }

    @Override
    protected Function<Schema, Number> getExactMethod() {
        return Schema::getMinimum;
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("number", "integer");
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        return String.valueOf(getExactMethod().apply(schema));
    }
}