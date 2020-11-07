package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@FieldFuzzer
public class InvalidValuesInEnumsFieldsFuzzer extends BaseBoundaryFieldFuzzer {

    public InvalidValuesInEnumsFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, CatsParams cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "invalid ENUM values";
    }

    @Override
    protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Collections.singletonList(StringSchema.class);
    }

    @Override
    protected String getBoundaryValue(Schema schema) {
        if (schema.getEnum() != null) {
            int length = String.valueOf(schema.getEnum().get(0)).length();
            return StringGenerator.generate("[A-Z]+", length, length);
        }
        return "";
    }

    @Override
    protected boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        return schema.getEnum() != null;
    }

    @Override
    public String description() {
        return "iterate through each ENUM field and send invalid values";
    }
}
