package com.endava.cats.fuzzer.fields;

import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class IntegerFieldsLeftBoundaryFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    public IntegerFieldsLeftBoundaryFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        super(sc, lr, cu);
    }

    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Arrays.asList(IntegerSchema.class);
    }

    @Override
    protected String getBoundaryValue(Schema schema) {
        return NumberGenerator.generateLeftBoundaryIntegerValue(schema);
    }

    @Override
    protected boolean hasBoundaryDefined(Schema schema) {
        return true;
    }

    @Override
    public String description() {
        return "iterate through each Integer field and send requests with outside the range values on the left side in the targeted field";
    }
}
