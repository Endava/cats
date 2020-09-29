package com.endava.cats.fuzzer.fields;

import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class StringsInNumericFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    public StringsInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        super(sc, lr, cu);
    }

    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Arrays.asList(NumberSchema.class, IntegerSchema.class);
    }

    @Override
    protected String getBoundaryValue(Schema schema) {
        return StringGenerator.generateRandomString();
    }

    @Override
    protected boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "strings in numeric fields";
    }

    @Override
    public String description() {
        return "iterate through each Integer (int, long) and Number field (float, double) and send requests having the `fuzz` string value in the targeted field";
    }
}