package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@FieldFuzzer
public class BooleanFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    public BooleanFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        super(sc, lr, cu);
    }

    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(BooleanSchema.class);
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
    public String description() {
        return "iterate through each Boolean field and send random strings in the targeted field";
    }
}