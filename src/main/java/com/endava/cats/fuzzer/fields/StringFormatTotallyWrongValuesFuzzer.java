package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.format.FormatGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@FieldFuzzer
@ConditionalOnProperty(value = "fuzzer.fields.StringFormatTotallyWrongValuesFuzzer.enabled", havingValue = "true")
public class StringFormatTotallyWrongValuesFuzzer extends BaseBoundaryFieldFuzzer {

    @Autowired
    public StringFormatTotallyWrongValuesFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "totally wrong values according to supplied format";
    }

    @Override
    public List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
        return Arrays.asList(StringSchema.class, DateSchema.class, DateTimeSchema.class, PasswordSchema.class, UUIDSchema.class, EmailSchema.class);
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        return FormatGenerator.from(schema.getFormat()).getGeneratorStrategy().getTotallyWrongValue();
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return "iterate through each String field and get its 'format' value (i.e. email, ip, uuid, date, datetime, etc); send requests with values which are totally wrong (i.e. abcd for email, 1244. for ip, etc)  in the targeted field";
    }
}
