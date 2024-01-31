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
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that iterates through each String field and get its 'format' value (i.e. email, ip, uuid, date, datetime, etc.)
 * and sends requests with values which are almost valid (i.e. email@yhoo. for email, 888.1.1. for ip, etc.).
 */
@Singleton
@FieldFuzzer
public class StringFormatAlmostValidValuesFuzzer extends BaseBoundaryFieldFuzzer {
    private final InvalidDataFormat invalidDataFormat;

    /**
     * Creates a new StringFormatAlmostValidValuesFuzzer instance.
     *
     * @param sc                the service caller
     * @param lr                the test case listener
     * @param cu                utility class
     * @param cp                files arguments
     * @param invalidDataFormat provider for invalid formats
     */
    public StringFormatAlmostValidValuesFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, InvalidDataFormat invalidDataFormat) {
        super(sc, lr, cu, cp);
        this.invalidDataFormat = invalidDataFormat;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "almost valid values according to supplied format";
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("string");
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        return invalidDataFormat.generator(schema, "").getAlmostValidValue();
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        return true;
    }

    @Override
    public String description() {
        return "iterate through each String field and get its 'format' value (i.e. email, ip, uuid, date, datetime, etc); send requests with values which are almost valid (i.e. email@yhoo. for email, 888.1.1. for ip, etc)";
    }
}