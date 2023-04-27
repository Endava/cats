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

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
public class DecimalFieldsRightBoundaryFuzzer extends BaseBoundaryFieldFuzzer {

    public DecimalFieldsRightBoundaryFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments fa) {
        super(sc, lr, cu, fa);
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("number");
    }

    @Override
    protected boolean isFuzzerWillingToFuzz(FuzzingData data, String fuzzedField) {
        return filesArguments.getRefData(data.getPath()).get(fuzzedField) == null;
    }

    @Override
    public Number getBoundaryValue(Schema schema) {
        return NumberGenerator.generateRightBoundaryDecimalValue(schema);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return "iterate through each Number field (either float or double) and send outside the range values on the right side in the targeted field";
    }
}