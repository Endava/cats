package com.endava.cats.fuzzer.fields;

import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ExtremePositiveValueInIntegerFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    public ExtremePositiveValueInIntegerFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        super(sc, lr, cu);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "extreme positive values";
    }

    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(IntegerSchema.class);
    }

    @Override
    protected String getBoundaryValue(Schema schema) {
        return NumberGenerator.getExtremePositiveIntegerValue(schema);
    }

    @Override
    protected boolean hasBoundaryDefined(Schema schema) {
        return true;
    }

    @Override
    public String description() {
        return String.format("iterate through each Integer field and send requests with the highest value possible (%s for int32 and %s for int64) in the targeted field", Long.MAX_VALUE, NumberGenerator.MOST_POSITIVE_INTEGER);
    }
}