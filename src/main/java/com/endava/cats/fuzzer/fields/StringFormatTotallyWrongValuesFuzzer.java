package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.format.api.InvalidDataFormat;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
public class StringFormatTotallyWrongValuesFuzzer extends BaseBoundaryFieldFuzzer {
    private final InvalidDataFormat invalidDataFormat;

    public StringFormatTotallyWrongValuesFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, InvalidDataFormat invalidDataFormat) {
        super(sc, lr, cu, cp);
        this.invalidDataFormat = invalidDataFormat;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "totally wrong values according to supplied format";
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("string");
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        return invalidDataFormat.generator(schema, "").getTotallyWrongValue();
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return "iterate through each String field and get its 'format' value (i.e. email, ip, uuid, date, datetime, etc); send requests with values which are totally wrong (i.e. abcd for email, 1244. for ip, etc)";
    }
}
