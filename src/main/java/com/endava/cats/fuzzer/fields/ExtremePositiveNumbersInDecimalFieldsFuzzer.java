package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

@Singleton
@FieldFuzzer
public class ExtremePositiveNumbersInDecimalFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    public ExtremePositiveNumbersInDecimalFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "extreme positive values";
    }


    @Override
    public List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(NumberSchema.class);
    }

    @Override
    public Number getBoundaryValue(Schema schema) {
        return NumberGenerator.getExtremePositiveDecimalValue(schema);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return String.format("iterate through each Number field and send %s for no format, %s for float and %s for double", NumberGenerator.MOST_POSITIVE_DECIMAL, Float.MAX_VALUE, Double.MAX_VALUE);
    }
}