package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends invalid values in enums. There is an argument {@code --allowInvalidEnumValues}
 * which will influence the expected response codes by this fuzzer.
 */
@Singleton
@FieldFuzzer
public class InvalidValuesInEnumsFieldsFuzzer extends BaseBoundaryFieldFuzzer {
    final ProcessingArguments processingArguments;

    /**
     * Creates a new InvalidValuesInEnumsFieldsFuzzer instance.
     *
     * @param sc                  the service caller
     * @param lr                  the test case listener
     * @param cp                  files arguments
     * @param processingArguments to get the {@code --allowInvalidEnumValues} argument value
     */
    public InvalidValuesInEnumsFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, ProcessingArguments processingArguments) {
        super(sc, lr, cp);
        this.processingArguments = processingArguments;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "invalid ENUM values";
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("string");
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        if (schema.getEnum() != null) {
            int length = String.valueOf(schema.getEnum().getFirst()).length();
            return StringGenerator.generate("[A-Z]+", length, length);
        }
        return null;
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        return schema.getEnum() != null;
    }

    @Override
    public String description() {
        return "iterate through each ENUM field and send invalid values";
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return processingArguments.isAllowInvalidEnumValues() ? ResponseCodeFamilyPredefined.TWOXX : ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return processingArguments.isAllowInvalidEnumValues() ? ResponseCodeFamilyPredefined.TWOXX : ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return processingArguments.isAllowInvalidEnumValues() ? ResponseCodeFamilyPredefined.TWOXX : ResponseCodeFamilyPredefined.FOURXX;
    }
}
