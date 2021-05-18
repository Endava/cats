package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@FieldFuzzer
@ConditionalOnProperty(value = "fuzzer.fields.ExtremeNegativeValueDecimalFieldsFuzzer.enabled", havingValue = "true")
public class ExtremeNegativeValueDecimalFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    public ExtremeNegativeValueDecimalFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "extreme negative values";
    }

    @Override
    public List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(NumberSchema.class);
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        return NumberGenerator.getExtremeNegativeDecimalValue(schema);
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return String.format("iterate through each Number field and send requests with the lowest value possible (%s for no format, %s for float and %s for double) in the targeted field", NumberGenerator.MOST_NEGATIVE_DECIMAL, -Float.MAX_VALUE, -Double.MAX_VALUE);
    }
}