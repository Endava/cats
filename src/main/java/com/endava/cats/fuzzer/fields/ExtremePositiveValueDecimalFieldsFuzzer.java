package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@FieldFuzzer
public class ExtremePositiveValueDecimalFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    public ExtremePositiveValueDecimalFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        super(sc, lr, cu);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "extreme positive values";
    }


    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(NumberSchema.class);
    }

    @Override
    protected String getBoundaryValue(Schema schema) {
        return NumberGenerator.getExtremePositiveDecimalValue(schema);
    }

    @Override
    protected boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return String.format("iterate through each Number field and send requests with the highest value possible (%s for no format, %s for float and %s for double) in the targeted field", NumberGenerator.MOST_POSITIVE_DECIMAL, Float.MAX_VALUE, Double.MAX_VALUE);
    }
}