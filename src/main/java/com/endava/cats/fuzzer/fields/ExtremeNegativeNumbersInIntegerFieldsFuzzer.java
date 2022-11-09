package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
public class ExtremeNegativeNumbersInIntegerFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    public ExtremeNegativeNumbersInIntegerFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "extreme negative values";
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("integer");
    }

    @Override
    public Number getBoundaryValue(Schema schema) {
        return NumberGenerator.getExtremeNegativeIntegerValue(schema);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return String.format("iterate through each Integer field and send %s for int32 and %s for int64", Long.MIN_VALUE, NumberGenerator.MOST_NEGATIVE_INTEGER);
    }
}