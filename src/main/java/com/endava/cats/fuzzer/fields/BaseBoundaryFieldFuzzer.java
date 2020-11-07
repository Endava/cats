package com.endava.cats.fuzzer.fields;

import com.endava.cats.generator.format.FormatGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Extend this class to provide concrete boundary values to be used for fuzzing.
 * The assumption is that expected response is a 4XX code when sending out of boundary values for the fuzzed fields.
 */
public abstract class BaseBoundaryFieldFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    @Autowired
    protected BaseBoundaryFieldFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, CatsParams cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "outside the boundary values";
    }

    @Override
    protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);

        if (this.fuzzedFieldHasAnAssociatedSchema(schema)) {
            logger.info("Field [{}] schema is [{}] and type [{}]", fuzzedField, schema.getClass().getSimpleName(), schema.getType());

            if (this.isFieldFuzzable(fuzzedField, data) && this.getBoundaryValue(schema) != null) {
                logger.info("[{}]. Start fuzzing...", getSchemasThatTheFuzzerWillApplyTo().stream().map(Class::getSimpleName).collect(Collectors.toSet()));
                return FuzzingStrategy.replace().withData(this.getBoundaryValue(schema));
            } else if (!this.hasBoundaryDefined(fuzzedField, data)) {
                logger.info("Boundaries not defined. Will skip fuzzing...");
                return FuzzingStrategy.skip().withData("No LEFT or RIGHT boundary info within the contract!");
            } else if (!this.isStringFormatRecognizable(schema) && isRequestSchemaMatchingFuzzerType(schema)) {
                logger.info("String format not supplied or not recognized [{}]", schema.getFormat());
                return FuzzingStrategy.skip().withData("String format not supplied or not recognized!");
            } else {
                logger.info("Not [{}]. Will skip fuzzing...", getSchemasThatTheFuzzerWillApplyTo().stream().map(Class::getSimpleName).collect(Collectors.toSet()));
            }
        }
        return FuzzingStrategy.skip().withData("Data type not matching " + getSchemasThatTheFuzzerWillApplyTo().stream().map(Class::getSimpleName).collect(Collectors.toSet()));
    }

    private boolean isFieldFuzzable(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        return this.isRequestSchemaMatchingFuzzerType(schema) && (this.hasBoundaryDefined(fuzzedField, data) || this.isStringFormatRecognizable(schema));
    }

    private boolean isStringFormatRecognizable(Schema schema) {
        return FormatGenerator.from(schema.getFormat()).compareTo(FormatGenerator.SKIP) != 0;
    }

    private boolean fuzzedFieldHasAnAssociatedSchema(Schema schema) {
        return schema != null;
    }

    private boolean isRequestSchemaMatchingFuzzerType(Schema schema) {
        return getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(currentSchema1 -> currentSchema1.isAssignableFrom(schema.getClass())) ||
                (schema.getType() != null && getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(currentSchema2 -> currentSchema2.getSimpleName().toLowerCase().startsWith(schema.getType())));
    }

    /**
     * Override this to provide information about which Schema the Fuzzer is applicable to.
     * For example if the current Schema is StringSchema, but the Fuzzer apply to Integer values then the Fuzzer will get skipped.
     *
     * @return schema type for which the Fuzzer will apply.
     */
    protected abstract List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo();

    /**
     * The value that will be used for fuzzing
     *
     * @param schema used to extract boundary information
     * @return a value to be used for fuzzing
     */
    protected abstract String getBoundaryValue(Schema schema);

    /**
     * Override this to provide information about whether the current field has boundaries defined or not. For example a String
     * field without minLength defined is not considered to have a left boundary
     *
     * @param fuzzedField used to extract boundary information
     * @param data        FuzzingData constructed by CATS
     * @return true if the filed has a boundary defined or false otherwise
     */
    protected abstract boolean hasBoundaryDefined(String fuzzedField, FuzzingData data);
}
