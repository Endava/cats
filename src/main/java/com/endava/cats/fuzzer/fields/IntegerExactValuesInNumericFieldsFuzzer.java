package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.Arrays;
import java.util.List;

public abstract class IntegerExactValuesInNumericFieldsFuzzer extends ExactValuesInStringFieldsFuzzer {

    public IntegerExactValuesInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, CatsParams catsParams) {
        super(sc, lr, cu, catsParams);
    }

    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Arrays.asList(NumberSchema.class, IntegerSchema.class);
    }

    @Override
    protected String getBoundaryValue(Schema schema) {
        return String.valueOf(getExactMethod().apply(schema));
    }
}
