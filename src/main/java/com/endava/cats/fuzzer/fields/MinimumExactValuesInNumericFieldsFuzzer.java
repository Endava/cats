package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.ExactValuesInFieldsFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Singleton
@FieldFuzzer
public class MinimumExactValuesInNumericFieldsFuzzer extends ExactValuesInFieldsFuzzer {

    public MinimumExactValuesInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
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
    public List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Arrays.asList(NumberSchema.class, IntegerSchema.class);
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        return String.valueOf(getExactMethod().apply(schema));
    }
}